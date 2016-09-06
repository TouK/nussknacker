package pl.touk.esp.engine.process

import java.lang.Iterable
import java.util.concurrent.TimeUnit

import cats.data.Validated.{Invalid, Valid}
import cats.data._
import com.codahale.metrics.{Histogram, SlidingTimeWindowReservoir}
import com.typesafe.config.Config
import org.apache.flink.api.common.functions._
import org.apache.flink.api.common.state._
import org.apache.flink.configuration.Configuration
import org.apache.flink.dropwizard.metrics.DropwizardHistogramWrapper
import org.apache.flink.streaming.api.TimeCharacteristic
import org.apache.flink.streaming.api.collector.selector.OutputSelector
import org.apache.flink.streaming.api.functions.{AssignerWithPeriodicWatermarks, AssignerWithPunctuatedWatermarks}
import org.apache.flink.streaming.api.operators.{AbstractStreamOperator, ChainingStrategy, OneInputStreamOperator}
import org.apache.flink.streaming.api.scala.function.util.ScalaFoldFunction
import org.apache.flink.streaming.api.scala.{StreamExecutionEnvironment, _}
import org.apache.flink.streaming.api.watermark.Watermark
import org.apache.flink.streaming.api.windowing.time.Time
import org.apache.flink.streaming.api.windowing.triggers.Trigger.TriggerContext
import org.apache.flink.streaming.api.windowing.triggers.{Trigger, TriggerResult}
import org.apache.flink.streaming.api.windowing.windows.TimeWindow
import org.apache.flink.streaming.runtime.streamrecord.StreamRecord
import org.apache.flink.util.Collector
import pl.touk.esp.engine.Interpreter
import pl.touk.esp.engine.api.{MetaData, _}
import pl.touk.esp.engine.api.exception.EspExceptionHandler
import pl.touk.esp.engine.api.process._
import pl.touk.esp.engine.compile.{PartSubGraphCompiler, ProcessCompilationError, ProcessCompiler}
import pl.touk.esp.engine.compiledgraph.part._
import pl.touk.esp.engine.definition.DefinitionExtractor.ObjectWithMethodDef
import pl.touk.esp.engine.definition.{ProcessObjectDefinitionExtractor, ProcessObjectFactory, ServiceDefinitionExtractor}
import pl.touk.esp.engine.graph.{EspProcess, param}
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

class FlinkProcessRegistrar(compileProcess: EspProcess => () => CompiledProcessWithDeps,
                            eventTimeMetricDuration: FiniteDuration,
                            checkpointInterval: FiniteDuration
                           ) {

  implicit def millisToTime(duration: Long): Time = Time.of(duration, TimeUnit.MILLISECONDS)

  def register(env: StreamExecutionEnvironment, process: EspProcess): Unit = {
    SpelHack.registerHackedSerializers(env)
    register(env, compileProcess(process))
  }

  private def register(env: StreamExecutionEnvironment, compiledProcessWithDeps: () => CompiledProcessWithDeps): Unit = {
    val process = compiledProcessWithDeps().compiledProcess
    env.setRestartStrategy(process.exceptionHandler.restartStrategy)
    process.metaData.parallelism.foreach(env.setParallelism)
    env.enableCheckpointing(checkpointInterval.toMillis)
    registerSourcePart(process.source)

    def registerSourcePart(part: SourcePart): Unit = {
      val timestampAssigner = part.obj.timestampAssigner

      timestampAssigner.foreach(_ => env.setStreamTimeCharacteristic(TimeCharacteristic.EventTime))

      val newStart = env
        .addSource[Any](part.obj.toFlinkSource)(part.obj.typeInformation)
      val withAssigned = timestampAssigner.map {
        case periodic: AssignerWithPeriodicWatermarks[Any@unchecked] =>
          newStart.assignTimestampsAndWatermarks(periodic)
        case punctuated: AssignerWithPunctuatedWatermarks[Any@unchecked] =>
          newStart.assignTimestampsAndWatermarks(punctuated)
      }.map(_.transform("even-time-meter", new EventTimeDelayMeterFunction("eventtimedelay", eventTimeMetricDuration)))
        .getOrElse(newStart)
        .map(new RateMeterFunction[Any]("source"))
        .flatMap(new InitialInterpretationFunction(compiledProcessWithDeps, part.source, Interpreter.InputParamName))
        .split(SplitFunction)

      registerParts(withAssigned, part.nextParts, part.ends)
    }

    def registerSubsequentPart[T](start: DataStream[InterpretationResult],
                                  processPart: SubsequentPart): Unit =
      processPart match {
        case part: AggregatePart =>
          val windowDef = start
            .keyBy(new AggregateKeyByFunction(compiledProcessWithDeps, part.aggregate))
            .timeWindow(part.durationInMillis, part.slideInMillis)

          val foldingFunction = part.foldingFun.getOrElse(JListFoldingFunction).asInstanceOf[FoldingFunction[Any]]

          val newStart = part.aggregate.triggerExpression
            .map(_ => windowDef.trigger(new AggregationTrigger(compiledProcessWithDeps, part.aggregate, part.aggregatedVar)))
            .getOrElse(windowDef)
            .fold(null, new WindowFoldingFunction(part.aggregatedVar, foldingFunction))
            .flatMap(new InitialInterpretationFunction(compiledProcessWithDeps, part.aggregate, part.aggregatedVar))
            .split(SplitFunction)

          registerParts(newStart, part.nextParts, part.ends)
        case part: SinkPart =>
          start
            .flatMap(new SinkInterpretationFunction(compiledProcessWithDeps, part.sink))
            .name(s"${part.id}-function")
            .map(new EndRateMeterFunction(part.ends))
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
        .map(new EndRateMeterFunction(ends))
    }

  }

}

object FlinkProcessRegistrar {

  import net.ceedubs.ficus.Ficus._

  private final val EndId = "$end"

  def apply(creator: ProcessConfigCreator, config: Config) = {
    def checkpointInterval() = config.as[FiniteDuration]("checkpointInterval")

    def eventTimeMetricDuration() = config.getOrElse[FiniteDuration]("metrics.eventTime.duration", 10.seconds)

    def compiler(sub: PartSubGraphCompiler): ProcessCompiler = {
      ProcessCompiler.apply(
        sub = sub,
        sourceFactories = creator.sourceFactories(config),
        sinkFactories = creator.sinkFactories(config),
        foldingFunctions = creator.foldingFunctions(config),
        exceptionHandlerFactory = creator.exceptionHandlerFactory(config)
      )
    }

    def compileProcess(process: EspProcess)() = {
      val services = creator.services(config)
      val servicesDefs = services.mapValues { service =>
        ObjectWithMethodDef(service, ServiceDefinitionExtractor.extractMethodDefinition(service))
      }
      val subCompiler = PartSubGraphCompiler.default(servicesDefs)
      val processCompiler = compiler(subCompiler)
      val compiledProcess = validateOrFailProcessCompilation(processCompiler.compile(process))
      val timeout = config.as[FiniteDuration]("timeout")
      CompiledProcessWithDeps(
        compiledProcess,
        new ServicesLifecycle(services.values.toSeq),
        subCompiler,
        Interpreter(servicesDefs, timeout, creator.listeners(config)),
        timeout
      )
    }

    new FlinkProcessRegistrar(
      compileProcess = compileProcess,
      eventTimeMetricDuration = eventTimeMetricDuration(),
      checkpointInterval = checkpointInterval()
    )
  }

  private def validateOrFailProcessCompilation[T](validated: ValidatedNel[ProcessCompilationError, T]): T = validated match {
    case Valid(r) => r
    case Invalid(err) => throw new scala.IllegalArgumentException(err.toList.mkString("Compilation errors: ", ", ", ""))
  }

  class InitialInterpretationFunction(compiledProcessWithDepsProvider: () => CompiledProcessWithDeps,
                                      node: SplittedNode,
                                      inputParamName: String) extends RichFlatMapFunction[Any, InterpretationResult] {

    private lazy implicit val ec = SynchronousExecutionContext.ctx
    private lazy val compiledProcessWithDeps = compiledProcessWithDepsProvider()
    private lazy val compiledNode = compiledProcessWithDeps.compileSubPart(node)
    import compiledProcessWithDeps._

    override def open(parameters: Configuration): Unit = {
      super.open(parameters)
      compiledProcessWithDeps.open(getRuntimeContext)
    }

    override def flatMap(input: Any, collector: Collector[InterpretationResult]): Unit = {
      val context = Context().withVariable(inputParamName, input)
      val result = exceptionHandler.recover {
        val resultFuture = interpreter.interpret(compiledNode, InterpreterMode.Traverse, metaData, context)
        Await.result(resultFuture, processTimeout)
      }(context)
      result.foreach(collector.collect)
    }

    override def close(): Unit = {
      super.close()
      compiledProcessWithDeps.close()
    }

  }

  class SinkInterpretationFunction(compiledProcessWithDepsProvider: () => CompiledProcessWithDeps,
                                   sink: splittednode.Sink) extends RichFlatMapFunction[InterpretationResult, InterpretationResult] {

    private lazy implicit val ec = SynchronousExecutionContext.ctx
    private lazy val compiledProcessWithDeps = compiledProcessWithDepsProvider()
    private lazy val compiledNode = compiledProcessWithDeps.compileSubPart(sink)
    import compiledProcessWithDeps._

    override def open(parameters: Configuration): Unit = {
      super.open(parameters)
      compiledProcessWithDeps.open(getRuntimeContext)
    }

    override def flatMap(input: InterpretationResult, collector: Collector[InterpretationResult]): Unit = {
      val result = exceptionHandler.recover {
        val resultFuture = interpreter.interpret(compiledNode, InterpreterMode.Traverse, metaData, input.finalContext)
        Await.result(resultFuture, processTimeout)
      }(input.finalContext)
      result.foreach(collector.collect)
    }

    override def close(): Unit = {
      super.close()
      compiledProcessWithDeps.close()
    }
  }

  class AggregateKeyByFunction(compiledProcessWithDepsProvider: () => CompiledProcessWithDeps,
                               node: splittednode.Aggregate) extends (InterpretationResult => String) with Serializable {

    private lazy implicit val ec = SynchronousExecutionContext.ctx
    private lazy val compiledProcessWithDeps = compiledProcessWithDepsProvider()
    private lazy val compiledNode = compiledProcessWithDeps.compileSubPart(node)
    import compiledProcessWithDeps._

    override def apply(result: InterpretationResult) = {
      val resultFuture = interpreter.interpret(compiledNode, InterpreterMode.AggregateKeyExpression, metaData, result.finalContext).map(_.output.toString)
      Await.result(resultFuture, processTimeout)
    }

  }

  class AggregationTrigger(compiledProcessWithDepsProvider: () => CompiledProcessWithDeps,
                           node: splittednode.Aggregate,
                           inputParamName: String) extends Trigger[AnyRef, TimeWindow] with Serializable {

    private lazy implicit val ec = SynchronousExecutionContext.ctx
    private lazy val compiledProcessWithDeps = compiledProcessWithDepsProvider()
    private lazy val compiledNode = compiledProcessWithDeps.compileSubPart(node)
    import compiledProcessWithDeps._

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
      val resultFuture = interpreter
        .interpret(compiledNode, InterpreterMode.AggregateTriggerExpression, metaData, foldResult, inputParamName)
        .map(_.output.toString.toBoolean)
      Await.result(resultFuture, processTimeout)
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

  class RateMeterFunction[T](groupId: String) extends RichMapFunction[T, T] {
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

  class EventTimeDelayMeterFunction[T](groupId: String, slidingWindow: FiniteDuration)
    extends AbstractStreamOperator[T] with OneInputStreamOperator[T, T] {

    setChainingStrategy(ChainingStrategy.ALWAYS)

    lazy val histogramMeter = new DropwizardHistogramWrapper(
      new Histogram(
        new SlidingTimeWindowReservoir(slidingWindow.toMillis, TimeUnit.MILLISECONDS)))

    override def open(): Unit = {
      super.open()

      getRuntimeContext.getMetricGroup
        .addGroup(groupId)
        .histogram("histogram", histogramMeter)
    }

    override def processElement(element: StreamRecord[T]): Unit = {
      if (element.hasTimestamp) {
        val delay = System.currentTimeMillis() - element.getTimestamp
        histogramMeter.update(delay)
      }
      output.collect(element)
    }

    override def processWatermark(mark: Watermark): Unit = {
      output.emitWatermark(mark)
    }

  }

  class EndRateMeterFunction(ends: Seq[End]) extends RichMapFunction[InterpretationResult, InterpretationResult] {

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