package pl.touk.esp.engine.process

import java.lang.Iterable
import java.util.concurrent.TimeUnit

import cats.data.Validated.{Invalid, Valid}
import cats.data._
import cats.std.list._
import com.typesafe.config.Config
import org.apache.flink.api.common.functions._
import org.apache.flink.api.common.state._
import org.apache.flink.configuration.Configuration
import org.apache.flink.streaming.api.TimeCharacteristic
import org.apache.flink.streaming.api.collector.selector.OutputSelector
import org.apache.flink.streaming.api.functions.sink.{RichSinkFunction, SinkFunction}
import org.apache.flink.streaming.api.scala.function.util.ScalaFoldFunction
import org.apache.flink.streaming.api.scala.{StreamExecutionEnvironment, _}
import org.apache.flink.streaming.api.windowing.time.Time
import org.apache.flink.streaming.api.windowing.triggers.Trigger.TriggerContext
import org.apache.flink.streaming.api.windowing.triggers.{Trigger, TriggerResult}
import org.apache.flink.streaming.api.windowing.windows.TimeWindow
import org.apache.flink.util.Collector
import org.slf4j.LoggerFactory
import pl.touk.esp.engine.Interpreter.ContextImpl
import pl.touk.esp.engine.api._
import pl.touk.esp.engine.api.process._
import pl.touk.esp.engine.compile.{ProcessCompilationError, ProcessCompiler}
import pl.touk.esp.engine.graph.EspProcess
import pl.touk.esp.engine.process.FlinkProcessRegistrar._
import pl.touk.esp.engine.process.util.SpelHack
import pl.touk.esp.engine.split.ProcessSplitter
import pl.touk.esp.engine.splittedgraph.part._
import pl.touk.esp.engine.splittedgraph.splittednode.{NextNode, PartRef, SplittedNode}
import pl.touk.esp.engine.splittedgraph.{SplittedProcess, splittednode}
import pl.touk.esp.engine.util.SynchronousExecutionContext
import pl.touk.esp.engine.util.metrics.InstantRateMeter
import pl.touk.esp.engine.{Interpreter, InterpreterConfig}

import scala.collection.JavaConverters._
import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.language.implicitConversions

class FlinkProcessRegistrar(interpreterConfig: () => InterpreterConfig,
                            sourceFactories: Map[String, SourceFactory[_]],
                            sinkFactories: Map[String, SinkFactory],
                            foldingFunctions: => Map[String, FoldingFunction[_]],
                            espExceptionHandlerProvider: () => EspExceptionHandler,
                            processTimeout: Duration,
                            compiler: => ProcessCompiler = ProcessCompiler.default) {

  implicit def millisToTime(duration: Long): Time = Time.of(duration, TimeUnit.MILLISECONDS)

  def register(env: StreamExecutionEnvironment, process: EspProcess): Unit = {
    SpelHack.registerHackedSerializers(env)
    val splittedProcess = ProcessSplitter.split(process)
    validateOrFail(compiler.validate(splittedProcess))
    register(env, splittedProcess)
  }

  private def register(env: StreamExecutionEnvironment, process: SplittedProcess): Unit = {
    registerSourcePart(process.source)

    def registerSourcePart(part: SourcePart): Unit = {
      val source = createSource(part)
      val timeExtractionFunction = source.timeExtractionFunction

      timeExtractionFunction.foreach(_ => env.setStreamTimeCharacteristic(TimeCharacteristic.EventTime))

      val newStart = env
        .addSource[Any](source.toFlinkSource)(source.typeInformation)
      //chyba nie ascending????
      val withAssigned = timeExtractionFunction.map(newStart.assignAscendingTimestamps).getOrElse(newStart)
        .map(new MeterFunction[Any]("source", process.metaData.id))
        .flatMap(new InitialInterpretationFunction(compiler, part.source, interpreterConfig, espExceptionHandlerProvider, process.metaData, Interpreter.InputParamName, processTimeout))
        .split(SplitFunction)
      registerParts(withAssigned, part.nextParts)
    }

    def registerSubsequentPart[T](start: AnyRef,
                                  processPart: SubsequentPart): Unit =
      processPart match {
        case part: AggregateDefinitionPart =>
          val windowDef = start.asInstanceOf[DataStream[InterpretationResult]]
            .keyBy(new AggregateKeyByFunction(compiler, part.aggregate, interpreterConfig, espExceptionHandlerProvider, processTimeout))
            .timeWindow(part.durationInMillis, part.slideInMillis)

          registerSubsequentPart(windowDef, part.nextPart)
        case part: AggregateTriggerPart =>
          val windowDef = start.asInstanceOf[WindowedStream[InterpretationResult, String, TimeWindow]]

          val foldingFunction = part.aggregate.foldingFunRef.map(ref =>
            foldingFunctions.getOrElse(ref, throw new IllegalStateException(s"Folding function $ref not found"))
          ).getOrElse(JListFoldingFunction).asInstanceOf[FoldingFunction[AnyRef]]

          val newStart = part.aggregate.triggerExpression
            .map(expr => windowDef.trigger(new AggregationTrigger(compiler, part.aggregatedVar,
              part.aggregate, interpreterConfig, process.metaData, processTimeout))).getOrElse(windowDef)
            .fold(null, new WindowFoldingFunction(part.aggregatedVar, foldingFunction))

          registerSubsequentPart(newStart, part.nextPart)
        case part: AfterAggregationPart =>
          val typedStart = start.asInstanceOf[DataStream[Any]] // List[Any]
          part.next match {
            case NextNode(node) =>
              val newStart = typedStart
                .flatMap(new InitialInterpretationFunction(compiler, node, interpreterConfig, espExceptionHandlerProvider, process.metaData, part.aggregatedVar, processTimeout))
                .split(SplitFunction)
              registerParts(newStart, part.nextParts)
            case PartRef(id) =>
              assert(part.nextParts.size == 1, "Aggregate ended up with part ref should have one next part")
              assert(part.nextParts.head.id == id, "Aggregate ended up with part ref should have one next part with the same id as in ref")
              val newStart = typedStart
                .map(new WrappingWithInterpretationResultFunction(NextPartReference(id), process.metaData, part.aggregatedVar))
              registerSubsequentPart(newStart, part.nextParts.head)
          }
        case part: SinkPart =>
          start.asInstanceOf[DataStream[InterpretationResult]]
            .flatMap(new SinkInterpretationFunction(process.metaData, compiler, part.sink, interpreterConfig, espExceptionHandlerProvider, processTimeout))
            .name(s"${part.id}-function")
            .addSink(new SinkSendingFunction(createSinkFunction(part), espExceptionHandlerProvider, processTimeout))
            .name(s"${part.id}-sink")
      }

    def registerParts(start: SplitStream[InterpretationResult],
                      nextParts: Seq[SubsequentPart]) = {
      nextParts.foreach { part =>
        registerSubsequentPart(start.select(part.id), part)
      }
      // TODO: register default sink
    }

    def createSource(part: SourcePart): Source[Any] = {
      val sourceType = part.ref.typ
      val sourceFactory = sourceFactories.getOrElse(sourceType, throw new scala.IllegalArgumentException(s"Missing source factory of type: $sourceType"))
      sourceFactory
        .create(process.metaData, part.ref.parameters.map(p => p.name -> p.value).toMap)
        .asInstanceOf[Source[Any]]
    }

    def createSinkFunction(part: SinkPart) = {
      val sinkType = part.ref.typ
      val sinkFactory = sinkFactories.getOrElse(sinkType, throw new IllegalArgumentException(s"Missing sink factory of type: $sinkType"))
      sinkFactory
        .create(process.metaData, part.ref.parameters.map(p => p.name -> p.value).toMap)
        .toFlinkFunction
    }

  }

}

object FlinkProcessRegistrar {

  private final val DefaultSinkId = "$"

  def apply(creator: ProcessConfigCreator, config: Config) = {
    val timeout = config.getDuration("timeout", TimeUnit.SECONDS).seconds

    new FlinkProcessRegistrar(() => new InterpreterConfig(creator.services(config), creator.listeners(config)),
      creator.sourceFactories(config), creator.sinkFactories(config),
      creator.foldingFunctions(config), () => creator.exceptionHandler(config), timeout)
  }

  private def validateOrFail[T](validated: ValidatedNel[ProcessCompilationError, T]): T = validated match {
    case Valid(r) => r
    case Invalid(err) => throw new scala.IllegalArgumentException(err.unwrap.mkString("Compilation errors: ", ", ", ""))
  }

  class InitialInterpretationFunction(compiler: => ProcessCompiler,
                                      node: SplittedNode,
                                      configProvider: () => InterpreterConfig,
                                      espExceptionHandlerProvider: () => EspExceptionHandler,
                                      metaData: MetaData,
                                      inputParamName: String,
                                      processTimeout: Duration) extends RichFlatMapFunction[Any, InterpretationResult] {

    lazy val config = configProvider()
    lazy implicit val ec = SynchronousExecutionContext.ctx
    private lazy val interpreter = new Interpreter(config)
    private lazy val compiledNode = validateOrFail(compiler.compile(node))
    private lazy val espExceptionHandler = espExceptionHandlerProvider()

    override def open(parameters: Configuration): Unit = {
      super.open(parameters)
      config.open
      espExceptionHandler.open()
    }

    override def flatMap(input: Any, collector: Collector[InterpretationResult]): Unit = {
      val result = espExceptionHandler.recover {
        val resultFuture = interpreter.interpret(compiledNode, metaData, input, inputParamName)
        Await.result(resultFuture, processTimeout)
      }(ContextImpl(metaData).withVariable(inputParamName, input))
      result.foreach(collector.collect)
    }

    override def close(): Unit = {
      super.close()
      config.close()
      espExceptionHandler.close()
    }

  }

  class WrappingWithInterpretationResultFunction(ref: PartReference,
                                                 metaData: MetaData,
                                                 inputParamName: String) extends MapFunction[Any, InterpretationResult] {
    override def map(value: Any): InterpretationResult = {
      val ctx = ContextImpl(metaData).withVariable(inputParamName, value)
      InterpretationResult(ref, null, ctx)
    }
  }

  class SinkInterpretationFunction(metaData: MetaData,
                                   compiler: => ProcessCompiler,
                                   sink: splittednode.Sink,
                                   configProvider: () => InterpreterConfig,
                                   espExceptionHandlerProvider: () => EspExceptionHandler,
                                   processTimeout: Duration) extends RichFlatMapFunction[InterpretationResult, InterpretationResult] {

    lazy val config = configProvider()
    lazy val instantRateMeter = new InstantRateMeter
    lazy implicit val ec = SynchronousExecutionContext.ctx
    lazy val logger = LoggerFactory.getLogger(classOf[FlinkProcessRegistrar])
    private lazy val interpreter = new Interpreter(config)
    private lazy val compiledNode = validateOrFail(compiler.compile(sink))
    private lazy val espExceptionHandler = espExceptionHandlerProvider()

    override def open(parameters: Configuration): Unit = {
      super.open(parameters)
      config.open
      espExceptionHandler.open()
      getRuntimeContext.getMetricGroup
        .addGroup(sink.id)
        .gauge[Double, InstantRateMeter]("instantRate", instantRateMeter)
      logger.info("Registered gauge for instantRate")
    }

    override def flatMap(input: InterpretationResult, collector: Collector[InterpretationResult]): Unit = {
      val result = espExceptionHandler.recover {
        val result = interpreter.interpret(compiledNode, input.finalContext)
        Await.result(result, processTimeout)
      }(input.finalContext)
      result.foreach(collector.collect)
      instantRateMeter.mark()
    }

    override def close(): Unit = {
      super.close()
      config.open
      espExceptionHandler.close()
    }
  }

  class SinkSendingFunction(underlying: RichMapFunction[InputWithExectutionContext, Future[Unit]],
                            espExceptionHandlerProvider: () => EspExceptionHandler,
                            processTimeout: Duration) extends RichSinkFunction[InterpretationResult] {

    private lazy val espExceptionHandler = espExceptionHandlerProvider()
    lazy implicit val ec = SynchronousExecutionContext.ctx

    override def setRuntimeContext(t: RuntimeContext): Unit = {
      super.setRuntimeContext(t)
      underlying.setRuntimeContext(t)
    }

    override def open(parameters: Configuration): Unit = {
      super.open(parameters)
      underlying.open(parameters)
      espExceptionHandler.open()
    }

    override def invoke(input: InterpretationResult): Unit = {
      espExceptionHandler.recover {
        val futureResult = underlying.map(InputWithExectutionContext(input.output, ec))
        Await.result(futureResult, processTimeout)
      }(input.finalContext)
    }

    override def close(): Unit = {
      super.close()
      underlying.close()
      espExceptionHandler.close()
    }

  }

  class AggregateKeyByFunction(compiler: => ProcessCompiler,
                               node: splittednode.AggregateDefinition,
                               configProvider: () => InterpreterConfig,
                               espExceptionHandlerProvider: () => EspExceptionHandler,
                               processTimeout: Duration) extends (InterpretationResult => Option[String]) with Serializable {

    private lazy val config = configProvider()
    private lazy val interpreter = new Interpreter(config)
    private lazy val espExceptionHandler = espExceptionHandlerProvider()
    private lazy val compiledNode = validateOrFail(compiler.compile(node))

    override def apply(result: InterpretationResult) = {
      implicit val ec = SynchronousExecutionContext.ctx
      espExceptionHandler.recover {
        val resultFuture = interpreter.interpret(compiledNode, result.finalContext).map(_.output.toString)
        Await.result(resultFuture, processTimeout)
      }(result.finalContext)
    }

  }

  class AggregationTrigger(compiler: => ProcessCompiler,
                           aggregateVar: String,
                           node: splittednode.AggregateTrigger,
                           config: () => InterpreterConfig,
                           metaData: MetaData,
                           processTimeout: Duration) extends Trigger[AnyRef, TimeWindow] with Serializable {

    private lazy val interpreter = new Interpreter(config())
    private lazy val compiledNode = validateOrFail(compiler.compile(node))

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
      implicit val ec = SynchronousExecutionContext.ctx
      val foldResult: AnyRef = getAggregatedState(ctx)
      val resultFuture = interpreter.interpret(compiledNode, metaData, foldResult).map(_.output.toString.toBoolean)
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


  class WindowFoldingFunction[T](aggregatedVar: String, foldingFun: => FoldingFunction[T])
    extends FoldFunction[InterpretationResult, T] {
    override def fold(accumulator: T, value: InterpretationResult) = {
      val result = value.finalContext[AnyRef](aggregatedVar)
      foldingFun.fold(result, Option(accumulator))
    }
  }

  class MeterFunction[T](name: String, groupId: String) extends RichMapFunction[T, T] {
    lazy val instantRateMeter = new InstantRateMeter

    override def open(parameters: Configuration): Unit = {
      super.open(parameters)

      getRuntimeContext.getMetricGroup
        .addGroup(groupId)
        .gauge[Double, InstantRateMeter](name, instantRateMeter)
    }

    override def map(value: T) = {
      instantRateMeter.mark()
      value
    }
  }

  object SplitFunction extends OutputSelector[InterpretationResult] {
    override def select(interpretationResult: InterpretationResult): Iterable[String] = {
      interpretationResult.reference match {
        case NextPartReference(id) => List(id).asJava
        case DefaultSinkReference => List(DefaultSinkId).asJava // TODO: default sink won't be registered
        case EndReference => throw new IllegalStateException("Non-sink interpretation shouldn't ended up by end reference")
      }
    }
  }

}