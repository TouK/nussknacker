package pl.touk.esp.engine.process

import java.lang.Iterable
import java.util.concurrent.TimeUnit

import cats.data.Validated.{Invalid, Valid}
import cats.data._
import cats.instances.list._
import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import org.apache.flink.api.common.functions._
import org.apache.flink.api.common.state._
import org.apache.flink.configuration.Configuration
import org.apache.flink.streaming.api.TimeCharacteristic
import org.apache.flink.streaming.api.collector.selector.OutputSelector
import org.apache.flink.streaming.api.functions.{AssignerWithPeriodicWatermarks, AssignerWithPunctuatedWatermarks}
import org.apache.flink.streaming.api.functions.timestamps.BoundedOutOfOrdernessTimestampExtractor
import org.apache.flink.streaming.api.scala.function.util.ScalaFoldFunction
import org.apache.flink.streaming.api.scala.{StreamExecutionEnvironment, _}
import org.apache.flink.streaming.api.windowing.time.Time
import org.apache.flink.streaming.api.windowing.triggers.Trigger.TriggerContext
import org.apache.flink.streaming.api.windowing.triggers.{Trigger, TriggerResult}
import org.apache.flink.streaming.api.windowing.windows.TimeWindow
import org.apache.flink.util.Collector
import pl.touk.esp.engine.Interpreter
import pl.touk.esp.engine.Interpreter.ContextImpl
import pl.touk.esp.engine.api._
import pl.touk.esp.engine.api.process._
import pl.touk.esp.engine.compile.{PartSubGraphCompilationError, PartSubGraphCompiler, ProcessCompilationError, ProcessCompiler}
import pl.touk.esp.engine.compiledgraph.CompiledProcessParts
import pl.touk.esp.engine.compiledgraph.part._
import pl.touk.esp.engine.definition.DefinitionExtractor.ObjectWithMethodDef
import pl.touk.esp.engine.definition.ServiceDefinitionExtractor
import pl.touk.esp.engine.graph.EspProcess
import pl.touk.esp.engine.process.FlinkProcessRegistrar._
import pl.touk.esp.engine.process.util.SpelHack
import pl.touk.esp.engine.splittedgraph.end.{DeadEnd, End, NormalEnd}
import pl.touk.esp.engine.splittedgraph.splittednode
import pl.touk.esp.engine.splittedgraph.splittednode.SplittedNode
import pl.touk.esp.engine.util.SynchronousExecutionContext
import pl.touk.esp.engine.util.metrics.InstantRateMeter

import scala.collection.JavaConverters._
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.language.implicitConversions

class FlinkProcessRegistrar(serviceLifecycleWithDependants: () => ServicesLifecycleWithDependants,
                            compiler: PartSubGraphCompiler => ProcessCompiler,
                            espExceptionHandlerProvider: () => EspExceptionHandler,
                            processTimeout: Duration) {

  implicit def millisToTime(duration: Long): Time = Time.of(duration, TimeUnit.MILLISECONDS)

  def register(env: StreamExecutionEnvironment, process: EspProcess): Unit = {
    val processCompiler = compiler(serviceLifecycleWithDependants().compiler)
    val compiledProcess = validateOrFailProcessCompilation(processCompiler.compile(process))
    SpelHack.registerHackedSerializers(env)
    register(env, compiledProcess)
  }

  private def register(env: StreamExecutionEnvironment, process: CompiledProcessParts): Unit = {
    process.metaData.parallelism.foreach(env.setParallelism)
    registerSourcePart(process.source)

    def registerSourcePart(part: SourcePart): Unit = {
      val timestampAssigner = part.obj.timestampAssigner

      timestampAssigner.foreach(_ => env.setStreamTimeCharacteristic(TimeCharacteristic.EventTime))

      val newStart = env
        .addSource[Any](part.obj.toFlinkSource)(part.obj.typeInformation)
      val withAssigned = timestampAssigner.collect {
        case periodic: AssignerWithPeriodicWatermarks[Any@unchecked] =>
          newStart.assignTimestampsAndWatermarks(periodic)
        case punctuated: AssignerWithPunctuatedWatermarks[Any@unchecked] =>
          newStart.assignTimestampsAndWatermarks(punctuated)
      }.getOrElse(newStart)
        .map(new MeterFunction[Any]("source"))
        .flatMap(new InitialInterpretationFunction(serviceLifecycleWithDependants,
          espExceptionHandlerProvider, part.source, Interpreter.InputParamName, process.metaData, processTimeout))
        .split(SplitFunction)

      registerParts(withAssigned, part.nextParts, part.ends)
    }

    def registerSubsequentPart[T](start: DataStream[InterpretationResult],
                                  processPart: SubsequentPart): Unit =
      processPart match {
        case part: AggregatePart =>
          val windowDef = start
            .keyBy(new AggregateKeyByFunction(serviceLifecycleWithDependants, espExceptionHandlerProvider, part.aggregate, process.metaData, processTimeout))
            .timeWindow(part.durationInMillis, part.slideInMillis)

          val foldingFunction = part.foldingFun.getOrElse(JListFoldingFunction).asInstanceOf[FoldingFunction[Any]]

          val newStart = part.aggregate.triggerExpression
            .map(expr => windowDef.trigger(new AggregationTrigger(serviceLifecycleWithDependants,
              espExceptionHandlerProvider, part.aggregate, part.aggregatedVar, process.metaData, processTimeout))).getOrElse(windowDef)
            .fold(null, new WindowFoldingFunction(part.aggregatedVar, foldingFunction))
            .flatMap(new InitialInterpretationFunction(serviceLifecycleWithDependants,
              espExceptionHandlerProvider, part.aggregate, part.aggregatedVar, process.metaData, processTimeout))
            .split(SplitFunction)

          registerParts(newStart, part.nextParts, part.ends)
        case part: SinkPart =>
          start
            .flatMap(new SinkInterpretationFunction(serviceLifecycleWithDependants, espExceptionHandlerProvider, part.sink, process.metaData, processTimeout))
            .name(s"${part.id}-function")
            .map(new EndMeterFunction(part.ends))
            .map(_.output)
            .addSink(part.obj.toFlinkFunction)
            .name(s"${part.id}-sink")
      }

    def registerParts(start: SplitStream[InterpretationResult],
                      nextParts: Seq[SubsequentPart],
                      ends: Seq[End]) = {
      nextParts.foreach { part =>
        registerSubsequentPart(start.select(part.id), part)
      }
      start.select(EndId)
        .map(new EndMeterFunction(ends))
    }

  }

}

object FlinkProcessRegistrar {

  private final val EndId = "$end"

  def apply(creator: ProcessConfigCreator, config: Config) = {
    val timeout = config.getDuration("timeout", TimeUnit.SECONDS).seconds

    def servicesLifecycleWithDependants() = {
      val services = creator.services(config)
      val servicesDefs = services.mapValues { service =>
        ObjectWithMethodDef(service, ServiceDefinitionExtractor.extractMethodDefinition(service))
      }
      val subCompiler = PartSubGraphCompiler.default(servicesDefs)
      ServicesLifecycleWithDependants(
        new ServicesLifecycle(services.values.toSeq),
        subCompiler,
        new Interpreter(servicesDefs, timeout, creator.listeners(config))
      )
    }

    def compiler(sub: PartSubGraphCompiler): ProcessCompiler = {
      ProcessCompiler.apply(
        sub = sub,
        sourceFactories = creator.sourceFactories(config),
        sinkFactories = creator.sinkFactories(config),
        foldingFunctions = creator.foldingFunctions(config)
      )
    }

    new FlinkProcessRegistrar(
      serviceLifecycleWithDependants = servicesLifecycleWithDependants,
      compiler = compiler,
      espExceptionHandlerProvider = () => creator.exceptionHandler(config),
      processTimeout = timeout)
  }

  private def validateOrFailProcessCompilation[T](validated: ValidatedNel[ProcessCompilationError, T]): T = validated match {
    case Valid(r) => r
    case Invalid(err) => throw new scala.IllegalArgumentException(err.toList.mkString("Compilation errors: ", ", ", ""))
  }

  private def validateOrFail[T](validated: ValidatedNel[PartSubGraphCompilationError, T]): T = validated match {
    case Valid(r) => r
    case Invalid(err) => throw new scala.IllegalArgumentException(err.toList.mkString("Compilation errors: ", ", ", ""))
  }

  class InitialInterpretationFunction(serviceLifecycleWithDependantsProvider: () => ServicesLifecycleWithDependants,
                                      espExceptionHandlerProvider: () => EspExceptionHandler,
                                      node: SplittedNode,
                                      inputParamName: String,
                                      metaData: MetaData,
                                      processTimeout: Duration) extends RichFlatMapFunction[Any, InterpretationResult] {

    private lazy implicit val ec = SynchronousExecutionContext.ctx
    private lazy val serviceLifecycleWithDependants = serviceLifecycleWithDependantsProvider()
    private lazy val compiledNode = validateOrFail(serviceLifecycleWithDependants.compiler.compile(node))
    private lazy val interpreter = serviceLifecycleWithDependants.interpreter
    private lazy val espExceptionHandler = espExceptionHandlerProvider()

    override def open(parameters: Configuration): Unit = {
      super.open(parameters)
      serviceLifecycleWithDependants.servicesLifecycle.open()
      espExceptionHandler.open(getRuntimeContext)
    }

    override def flatMap(input: Any, collector: Collector[InterpretationResult]): Unit = {
      val result = espExceptionHandler.recover {
        val resultFuture = interpreter.interpret(compiledNode, InterpreterMode.Traverse, metaData, input, inputParamName)
        Await.result(resultFuture, processTimeout)
      }(ContextImpl().withVariable(inputParamName, input), metaData)
      result.foreach(collector.collect)
    }

    override def close(): Unit = {
      super.close()
      serviceLifecycleWithDependants.servicesLifecycle.close()
      espExceptionHandler.close()
    }

  }

  class SinkInterpretationFunction(serviceLifecycleWithDependantsProvider: () => ServicesLifecycleWithDependants,
                                   espExceptionHandlerProvider: () => EspExceptionHandler,
                                   sink: splittednode.Sink,
                                   metaData: MetaData,
                                   processTimeout: Duration) extends RichFlatMapFunction[InterpretationResult, InterpretationResult] with LazyLogging {

    private lazy implicit val ec = SynchronousExecutionContext.ctx
    private lazy val serviceLifecycleWithDependants = serviceLifecycleWithDependantsProvider()
    private lazy val compiledNode = validateOrFail(serviceLifecycleWithDependants.compiler.compile(sink))
    private lazy val interpreter = serviceLifecycleWithDependants.interpreter
    private lazy val espExceptionHandler = espExceptionHandlerProvider()

    override def open(parameters: Configuration): Unit = {
      super.open(parameters)
      serviceLifecycleWithDependants.servicesLifecycle.open()
      espExceptionHandler.open(getRuntimeContext)
      logger.info("Registered gauge for instantRate")
    }

    override def flatMap(input: InterpretationResult, collector: Collector[InterpretationResult]): Unit = {
      val result = espExceptionHandler.recover {
        val resultFuture = interpreter.interpret(compiledNode, InterpreterMode.Traverse, input.finalContext, metaData)
        Await.result(resultFuture, processTimeout)
      }(input.finalContext, metaData)
      result.foreach(collector.collect)
    }

    override def close(): Unit = {
      super.close()
      serviceLifecycleWithDependants.servicesLifecycle.close()
      espExceptionHandler.close()
    }
  }

  class AggregateKeyByFunction(serviceLifecycleWithDependantsProvider: () => ServicesLifecycleWithDependants,
                               espExceptionHandlerProvider: () => EspExceptionHandler,
                               node: splittednode.Aggregate,
                               metaData: MetaData,
                               processTimeout: Duration) extends (InterpretationResult => Option[String]) with Serializable {

    private lazy implicit val ec = SynchronousExecutionContext.ctx
    private lazy val serviceLifecycleWithDependants = serviceLifecycleWithDependantsProvider()
    private lazy val compiledNode = validateOrFail(serviceLifecycleWithDependants.compiler.compile(node))
    private lazy val interpreter = serviceLifecycleWithDependants.interpreter
    private lazy val espExceptionHandler = espExceptionHandlerProvider()

    override def apply(result: InterpretationResult) = {
      espExceptionHandler.recover {
        val resultFuture = interpreter.interpret(compiledNode, InterpreterMode.AggregateKeyExpression, result.finalContext, metaData).map(_.output.toString)
        Await.result(resultFuture, processTimeout)
      }(result.finalContext, metaData)
    }

  }

  class AggregationTrigger(serviceLifecycleWithDependantsProvider: () => ServicesLifecycleWithDependants,
                           espExceptionHandlerProvider: () => EspExceptionHandler,
                           node: splittednode.Aggregate,
                           inputParamName: String,
                           metaData: MetaData,
                           processTimeout: Duration) extends Trigger[AnyRef, TimeWindow] with Serializable {

    private lazy implicit val ec = SynchronousExecutionContext.ctx
    private lazy val serviceLifecycleWithDependants = serviceLifecycleWithDependantsProvider()
    private lazy val compiledNode = validateOrFail(serviceLifecycleWithDependants.compiler.compile(node))
    private lazy val interpreter = serviceLifecycleWithDependants.interpreter
    private lazy val espExceptionHandler = espExceptionHandlerProvider()

    override def onElement(element: scala.AnyRef, timestamp: Long, window: TimeWindow, ctx: TriggerContext) = {
      if (timestamp > window.maxTimestamp()) {
        TriggerResult.PURGE
      } else if (currentExceedsThreshold(ctx)) {
        TriggerResult.FIRE_AND_PURGE
      } else {
        TriggerResult.CONTINUE
      }
    }

    def currentExceedsThreshold(ctx: TriggerContext): Boolean = {
      val foldResult: AnyRef = getAggregatedState(ctx)
      espExceptionHandler.recover {
        val resultFuture = interpreter
          .interpret(compiledNode, InterpreterMode.AggregateTriggerExpression, metaData, foldResult, inputParamName)
          .map(_.output.toString.toBoolean)
        Await.result(resultFuture, processTimeout)
      }(ContextImpl().withVariable(inputParamName, foldResult), metaData).getOrElse(false) // false?
    }

    def getAggregatedState(ctx: TriggerContext): AnyRef = {
      //tutaj podajemy te nulle, bo tam wartosci sa wypelniane kiedy chcemy modyfikowac stan
      //to jest troche hack, bo polegamy na obecnej implementacji okien, ale nie wiem jak to inaczej zrobic...
      ctx.getPartitionedState[FoldingState[AnyRef, AnyRef]](
        new FoldingStateDescriptor[AnyRef, AnyRef]("window-contents", null,
          new ScalaFoldFunction[AnyRef, AnyRef]((_, _) => null), classOf[AnyRef])).get()
    }

    override def onProcessingTime(time: Long, window: TimeWindow, ctx: TriggerContext) = TriggerResult.CONTINUE

    override def onEventTime(time: Long, window: TimeWindow, ctx: TriggerContext) = TriggerResult.CONTINUE

  }


  class WindowFoldingFunction[T](aggregatedVar: String, foldingFun: FoldingFunction[T])
    extends FoldFunction[InterpretationResult, T] {
    override def fold(accumulator: T, value: InterpretationResult) = {
      val result = value.finalContext[AnyRef](aggregatedVar)
      foldingFun.fold(result, Option(accumulator))
    }
  }

  class MeterFunction[T](groupId: String) extends RichMapFunction[T, T] {
    lazy val instantRateMeter = new InstantRateMeter

    override def open(parameters: Configuration): Unit = {
      super.open(parameters)

      getRuntimeContext.getMetricGroup
        .addGroup(groupId)
        .gauge[Double, InstantRateMeter]("instantRate", instantRateMeter)
    }

    override def map(value: T) = {
      instantRateMeter.mark()
      value
    }
  }

  class EndMeterFunction(ends: Seq[End]) extends RichMapFunction[InterpretationResult, InterpretationResult] {

    @transient private var meterByReference: Map[PartReference, InstantRateMeter] = _

    override def open(parameters: Configuration): Unit = {
      super.open(parameters)

      val parentGroupForNormalEnds = getRuntimeContext.getMetricGroup.addGroup("end")
      val parentGroupForDeadEnds = getRuntimeContext.getMetricGroup.addGroup("dead_end")

      def registerRateMeter(end: End) = {
        val baseGroup = end match {
          case normal: NormalEnd => parentGroupForNormalEnds
          case dead: DeadEnd => parentGroupForDeadEnds
        }
        baseGroup
          .addGroup(end.nodeId)
          .gauge[Double, InstantRateMeter]("instantRate", new InstantRateMeter)
      }

      meterByReference = ends.map { end =>
        val reference = end match {
          case NormalEnd(nodeId) =>  EndReference(nodeId)
          case DeadEnd(nodeId) =>  DeadEndReference(nodeId)
        }
        reference -> registerRateMeter(end)
      }.toMap[PartReference, InstantRateMeter]
    }

    override def map(value: InterpretationResult) = {
      val meter = meterByReference.getOrElse(value.reference, throw new IllegalArgumentException("Unexpected reference: " + value.reference))
      meter.mark()
      value
    }
  }

  object SplitFunction extends OutputSelector[InterpretationResult] {
    override def select(interpretationResult: InterpretationResult): Iterable[String] = {
      interpretationResult.reference match {
        case NextPartReference(id) => List(id).asJava
        case _: EndingReference => List(EndId).asJava
      }
    }
  }

}