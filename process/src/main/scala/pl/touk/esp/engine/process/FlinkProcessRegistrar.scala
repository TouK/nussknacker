package pl.touk.esp.engine.process

import java.lang.Iterable
import java.util.Collections
import java.util.concurrent.TimeUnit

import cats.data.Validated.{Invalid, Valid}
import cats.data._
import com.codahale.metrics.{Histogram, SlidingTimeWindowReservoir}
import com.typesafe.config.Config
import org.apache.flink.api.common.functions._
import org.apache.flink.api.java.tuple
import org.apache.flink.configuration.Configuration
import org.apache.flink.dropwizard.metrics.DropwizardHistogramWrapper
import org.apache.flink.metrics.Gauge
import org.apache.flink.streaming.api.TimeCharacteristic
import org.apache.flink.streaming.api.collector.selector.OutputSelector
import org.apache.flink.streaming.api.functions.{AssignerWithPeriodicWatermarks, AssignerWithPunctuatedWatermarks}
import org.apache.flink.streaming.api.operators.{AbstractStreamOperator, ChainingStrategy, OneInputStreamOperator, StreamOperator}
import org.apache.flink.streaming.api.scala.{StreamExecutionEnvironment, _}
import org.apache.flink.streaming.api.watermark.Watermark
import org.apache.flink.streaming.api.windowing.time.Time
import org.apache.flink.streaming.runtime.operators.windowing.WindowOperator
import org.apache.flink.streaming.runtime.streamrecord.StreamRecord
import org.apache.flink.util.Collector
import pl.touk.esp.engine.Interpreter
import pl.touk.esp.engine.api._
import pl.touk.esp.engine.api.process._
import pl.touk.esp.engine.compile.{PartSubGraphCompiler, ProcessCompilationError, ProcessCompiler}
import pl.touk.esp.engine.compiledgraph.part._
import pl.touk.esp.engine.definition.DefinitionExtractor.{ClazzRef, ObjectWithMethodDef}
import pl.touk.esp.engine.definition.{CustomNodeInvoker, ProcessDefinitionExtractor, ServiceDefinitionExtractor}
import pl.touk.esp.engine.flink.api.exception.FlinkEspExceptionHandler
import pl.touk.esp.engine.flink.api.process.{FlinkSink, FlinkSource}
import pl.touk.esp.engine.flink.util.ContextInitializingFunction
import pl.touk.esp.engine.flink.util.metrics.InstantRateMeter
import pl.touk.esp.engine.graph.{EspProcess, node}
import pl.touk.esp.engine.process.FlinkProcessRegistrar._
import pl.touk.esp.engine.process.util.Serializers
import pl.touk.esp.engine.splittedgraph.end.{DeadEnd, End, NormalEnd}
import pl.touk.esp.engine.splittedgraph.splittednode
import pl.touk.esp.engine.splittedgraph.splittednode.{NextNode, PartRef, SplittedNode}
import pl.touk.esp.engine.util.SynchronousExecutionContext

import scala.collection.JavaConversions._
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.language.implicitConversions

class FlinkProcessRegistrar(compileProcess: EspProcess => () => CompiledProcessWithDeps,
                            eventTimeMetricDuration: FiniteDuration,
                            checkpointInterval: FiniteDuration
                           ) {

  implicit def millisToTime(duration: Long): Time = Time.of(duration, TimeUnit.MILLISECONDS)

  def register(env: StreamExecutionEnvironment, process: EspProcess): Unit = {
    Serializers.registerSerializers(env)
    register(env, compileProcess(process))

    initializeStateDescriptors(env)

  }

  //Flink przy serializacji grafu (StateDescriptor:233) inicjalizuje KryoSerializer bez konfiguracji z env
  //to jest chyba blad - do zgloszenia (?)
  //TODO: czy to jedyny przypadek kiedy powinnismy tak robic??
  def initializeStateDescriptors(env: StreamExecutionEnvironment): Unit = {
    val config = env.getConfig
    env.getStreamGraph.getOperators.toSet[tuple.Tuple2[Integer, StreamOperator[_]]].map(_.f1).collect {
      case window:WindowOperator[_, _, _, _, _] => window.getStateDescriptor.initializeSerializerUnlessSet(config)
    }
  }

  private def register(env: StreamExecutionEnvironment, compiledProcessWithDeps: () => CompiledProcessWithDeps): Unit = {
    val process = compiledProcessWithDeps().compiledProcess
    //FIXME: ladniej bez casta
    env.setRestartStrategy(process.exceptionHandler.asInstanceOf[FlinkEspExceptionHandler].restartStrategy)
    process.metaData.parallelism.foreach(env.setParallelism)
    env.enableCheckpointing(checkpointInterval.toMillis)
    registerSourcePart(process.source)

    def registerSourcePart(part: SourcePart): Unit = {
      //FIXME: ladniej bez casta
      val source = part.obj.asInstanceOf[FlinkSource[Any]]

      val timestampAssigner = source.timestampAssigner

      timestampAssigner.foreach(_ => env.setStreamTimeCharacteristic(TimeCharacteristic.EventTime))

      val newStart = env
        .addSource[Any](source.toFlinkSource)(source.typeInformation)
        .name(s"${process.metaData.id}-source")
      val withAssigned = timestampAssigner.map {
        case periodic: AssignerWithPeriodicWatermarks[Any@unchecked] =>
          newStart.assignTimestampsAndWatermarks(periodic)
        case punctuated: AssignerWithPunctuatedWatermarks[Any@unchecked] =>
          newStart.assignTimestampsAndWatermarks(punctuated)
      }.map(_.transform("even-time-meter", new EventTimeDelayMeterFunction("eventtimedelay", eventTimeMetricDuration)))
        .getOrElse(newStart)
        .map(new RateMeterFunction[Any]("source"))
          .map(InitContextFunction(process.metaData.id, part.node.id))
        .flatMap(new InterpretationFunction(compiledProcessWithDeps, part.node))
        .name(s"${process.metaData.id}-source-interpretation")
        .split(SplitFunction)

      registerParts(withAssigned, part.nextParts, part.ends)
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

    def registerSubsequentPart[T](start: DataStream[InterpretationResult],
                                  processPart: SubsequentPart): Unit =
      processPart match {
        case part@SinkPart(sink: FlinkSink, _) =>
          start
            .flatMap(new SinkInterpretationFunction(compiledProcessWithDeps, part.node))
            .name(s"${process.metaData.id}-${part.id}-function")
            .map(new EndRateMeterFunction(part.ends))
            .map(_.output)
            .addSink(sink.toFlinkFunction)
            .name(s"${process.metaData.id}-${part.id}-sink")
        case part:SinkPart =>
          throw new IllegalArgumentException(s"Process can only use flink sinks, instead given: ${part.obj}")
        case SplitPart(splitNode, nexts) =>
          val nextIds = nexts.map(_.next.id)
          //TODO: bug we flinku jesli sa 2 splity pod rzad - to jest workaround, trzeba zglosic i poprawic...
          val newStart = start.map(identity[InterpretationResult] _).split(_ => nextIds)
          nexts.foreach {
            //FIXME: czy to wszystko tutaj jest w porzadku???
            case NextWithParts(NextNode(nextNode), parts, ends) =>
              val interpreted = newStart.select(nextNode.id)
                  .map(_.finalContext)
                  .flatMap(new InterpretationFunction(compiledProcessWithDeps, nextNode))
                  .name(s"${process.metaData.id}-${nextNode.id}-interpretation")
                  .split(SplitFunction)
              registerParts(interpreted, parts, ends)
            case NextWithParts(PartRef(id), parts, ends) =>
              val splitted = newStart.select(id).split(SplitFunction)
              registerParts(splitted, parts, ends)
          }
        case part@CustomNodePart(executor:
          CustomNodeInvoker[((DataStream[InterpretationResult], FiniteDuration) => DataStream[ValueWithContext[_]])@unchecked],
              node, nextParts, ends) =>

          val newStart = executor.run(compiledProcessWithDeps)(start, compiledProcessWithDeps().processTimeout)
              .map(ir => ir.context.withVariable(node.data.outputVar, ir.value))
              .flatMap(new InterpretationFunction(compiledProcessWithDeps, node))
              .name(s"${process.metaData.id}-${node.id}-customNodeInterpretation")
              .split(SplitFunction)

          registerParts(newStart, nextParts, ends)
        case e:CustomNodePart =>
          throw new IllegalArgumentException(s"Unknown CustomNodeExecutor: ${e.customNodeInvoker}")
      }
  }
}

object FlinkProcessRegistrar {

  import net.ceedubs.ficus.Ficus._
  import pl.touk.esp.engine.util.Implicits._

  private final val EndId = "$end"

  def apply(creator: ProcessConfigCreator, config: Config,
            definitionsPostProcessor: (ProcessDefinitionExtractor.ProcessDefinition[ObjectWithMethodDef] => ProcessDefinitionExtractor.ProcessDefinition[ObjectWithMethodDef]) = identity,
              additionalListeners: List[ProcessListener] = List()) = {
    def checkpointInterval() = config.as[FiniteDuration]("checkpointInterval")

    def eventTimeMetricDuration() = config.getOrElse[FiniteDuration]("metrics.eventTime.duration", 10.seconds)

    def compiler(sub: PartSubGraphCompiler): ProcessCompiler = {
      val definitions = ProcessDefinitionExtractor.extractObjectWithMethods(creator, config)
      new ProcessCompiler(sub, definitionsPostProcessor(definitions))
    }


    def compileProcess(process: EspProcess)() = {
      val services = creator.services(config)
      val servicesDefs = services.mapValuesNow { service => ObjectWithMethodDef(service, ServiceDefinitionExtractor) }
      val subCompiler = PartSubGraphCompiler.default(servicesDefs, creator.globalProcessVariables(config).mapValuesNow(v => ClazzRef(v.value)))
      val processCompiler = compiler(subCompiler)
      val compiledProcess = validateOrFailProcessCompilation(processCompiler.compile(process))
      val timeout = config.as[FiniteDuration]("timeout")
      CompiledProcessWithDeps(
        compiledProcess,
        new ServicesLifecycle(services.values.map(_.value).toSeq),
        subCompiler,
        Interpreter(servicesDefs, timeout, creator.listeners(config) ++ additionalListeners),
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

  class InterpretationFunction(compiledProcessWithDepsProvider: () => CompiledProcessWithDeps,
                               node: SplittedNode[_]) extends RichFlatMapFunction[Context, InterpretationResult] {

    private lazy implicit val ec = SynchronousExecutionContext.ctx
    private lazy val compiledProcessWithDeps = compiledProcessWithDepsProvider()
    private lazy val compiledNode = compiledProcessWithDeps.compileSubPart(node)
    import compiledProcessWithDeps._

    override def open(parameters: Configuration): Unit = {
      super.open(parameters)
      compiledProcessWithDeps.open(getRuntimeContext)
    }

    override def flatMap(input: Context, collector: Collector[InterpretationResult]): Unit = {
      val result = exceptionHandler.recover {
        val resultFuture = interpreter.interpret(compiledNode, InterpreterMode.Traverse, metaData, input)
        Await.result(resultFuture, processTimeout)
      }(input)
      result.foreach(collector.collect)
    }

    override def close(): Unit = {
      super.close()
      compiledProcessWithDeps.close()
    }

  }

  class SinkInterpretationFunction(compiledProcessWithDepsProvider: () => CompiledProcessWithDeps,
                                   sink: splittednode.SplittedNode[node.Sink]) extends RichFlatMapFunction[InterpretationResult, InterpretationResult] {

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

  case class InitContextFunction(processId: String, taskName: String) extends RichMapFunction[Any, Context] with ContextInitializingFunction {

    override def open(parameters: Configuration) = {
      init(getRuntimeContext)
    }

    override def map(input: Any) = newContext.withVariable(Interpreter.InputParamName, input)

  }

  class EventTimeDelayMeterFunction[T](groupId: String, slidingWindow: FiniteDuration)
    extends AbstractStreamOperator[T] with OneInputStreamOperator[T, T] {

    setChainingStrategy(ChainingStrategy.ALWAYS)

    lazy val histogramMeter = new DropwizardHistogramWrapper(
      new Histogram(
        new SlidingTimeWindowReservoir(slidingWindow.toMillis, TimeUnit.MILLISECONDS)))

    lazy val minimalDelayGauge = new Gauge[Long] {
      val now = System.currentTimeMillis()
      override def getValue = now - lastElement.getOrElse(now)
    }

    var lastElement : Option[Long] = None

    override def open(): Unit = {
      super.open()

      val group = getRuntimeContext.getMetricGroup.addGroup(groupId)
      group.histogram("histogram", histogramMeter)
      group.gauge[Long, Gauge[Long]]("minimalDelay", minimalDelayGauge)
    }

    override def processElement(element: StreamRecord[T]): Unit = {
      if (element.hasTimestamp) {
        val timestamp = element.getTimestamp
        val delay = System.currentTimeMillis() - timestamp
        histogramMeter.update(delay)
        lastElement = Some(lastElement.fold(timestamp)(math.max(_, timestamp)))
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
      Collections.singletonList(interpretationResult.reference match {
        case NextPartReference(id) => id
        case _: EndingReference => EndId
      })
    }
  }

}