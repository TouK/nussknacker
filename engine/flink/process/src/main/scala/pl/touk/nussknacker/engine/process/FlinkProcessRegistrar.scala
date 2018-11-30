package pl.touk.nussknacker.engine.process

import java.lang.Iterable
import java.util.Collections
import java.util.concurrent.TimeUnit

import com.codahale.metrics.{Histogram, SlidingTimeWindowReservoir}
import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import org.apache.flink.api.common.functions._
import org.apache.flink.api.java.tuple
import org.apache.flink.configuration.Configuration
import org.apache.flink.dropwizard.metrics.DropwizardHistogramWrapper
import org.apache.flink.metrics.{Counter, Gauge}
import org.apache.flink.runtime.state.AbstractStateBackend
import org.apache.flink.streaming.api.datastream
import org.apache.flink.streaming.api.TimeCharacteristic
import org.apache.flink.streaming.api.collector.selector.OutputSelector
import org.apache.flink.streaming.api.environment.RemoteStreamEnvironment
import org.apache.flink.streaming.api.functions.async.{ResultFuture, RichAsyncFunction}
import org.apache.flink.streaming.api.functions.sink.{RichSinkFunction, SinkFunction}
import org.apache.flink.streaming.api.functions.{AssignerWithPeriodicWatermarks, AssignerWithPunctuatedWatermarks}
import org.apache.flink.streaming.api.operators.{AbstractStreamOperator, ChainingStrategy, OneInputStreamOperator, StreamOperator}
import org.apache.flink.streaming.api.scala.{StreamExecutionEnvironment, _}
import org.apache.flink.streaming.api.watermark.Watermark
import org.apache.flink.streaming.api.windowing.time.Time
import org.apache.flink.streaming.runtime.operators.windowing.WindowOperator
import org.apache.flink.streaming.runtime.streamrecord.StreamRecord
import org.apache.flink.runtime.execution.librarycache.FlinkUserCodeClassLoaders
import org.apache.flink.util.Collector
import pl.touk.nussknacker.engine.Interpreter
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.exception.EspExceptionInfo
import pl.touk.nussknacker.engine.api.process.AsyncExecutionContextPreparer
import pl.touk.nussknacker.engine.api.test.InvocationCollectors.{SinkInvocationCollector, SplitInvocationCollector}
import pl.touk.nussknacker.engine.api.test.TestRunId
import pl.touk.nussknacker.engine.api.typed.typing.Typed
import pl.touk.nussknacker.engine.compile.ProcessCompilationError.NodeId
import pl.touk.nussknacker.engine.compile.ValidationContext
import pl.touk.nussknacker.engine.compiledgraph.part._
import pl.touk.nussknacker.engine.definition.CustomNodeInvoker
import pl.touk.nussknacker.engine.flink.api.process.{FlinkCustomNodeContext, FlinkCustomStreamTransformation, FlinkSink, FlinkSource}
import pl.touk.nussknacker.engine.flink.util.ContextInitializingFunction
import pl.touk.nussknacker.engine.flink.util.metrics.InstantRateMeterWithCount
import pl.touk.nussknacker.engine.flink.util.sink.EmptySink
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.process.FlinkProcessRegistrar._
import pl.touk.nussknacker.engine.process.compiler.{CompiledProcessWithDeps, FlinkProcessCompiler}
import pl.touk.nussknacker.engine.process.util.StateConfiguration.RocksDBStateBackendConfig
import pl.touk.nussknacker.engine.process.util.{MetaDataExtractor, Serializers, StateConfiguration, UserClassLoader}
import pl.touk.nussknacker.engine.splittedgraph.end.{DeadEnd, End, NormalEnd}
import pl.touk.nussknacker.engine.splittedgraph.splittednode.{NextNode, PartRef, SplittedNode}
import pl.touk.nussknacker.engine.util.{SynchronousExecutionContext, ThreadUtils}
import pl.touk.nussknacker.engine.util.metrics.RateMeter

import scala.collection.JavaConversions._
import scala.concurrent.{Await, ExecutionContext}
import scala.concurrent.duration._
import scala.language.implicitConversions
import scala.util.{Failure, Success}
import scala.util.control.NonFatal


class FlinkProcessRegistrar(compileProcess: (EspProcess, ProcessVersion) => (ClassLoader) => CompiledProcessWithDeps,
                            eventTimeMetricDuration: FiniteDuration,
                            checkpointInterval: FiniteDuration,
                            enableObjectReuse: Boolean, diskStateBackend: Option[AbstractStateBackend]
                           ) extends LazyLogging {

  implicit def millisToTime(duration: Long): Time = Time.of(duration, TimeUnit.MILLISECONDS)

  def register(env: StreamExecutionEnvironment, process: EspProcess, processVersion: ProcessVersion, testRunId: Option[TestRunId] = None): Unit = {
    Serializers.registerSerializers(env)
    if (enableObjectReuse) {
      env.getConfig.enableObjectReuse()
      logger.info("Object reuse enabled")
    }

    usingRightClassloader(env) {
      register(env, compileProcess(process, processVersion), testRunId)
    }
    initializeStateDescriptors(env)
  }

  private def usingRightClassloader(env: StreamExecutionEnvironment)(action: => Unit): Unit = {
    if (!env.getJavaEnv.isInstanceOf[RemoteStreamEnvironment]) {
      val flinkLoaderSimulation =  FlinkUserCodeClassLoaders.childFirst(Array.empty, Thread.currentThread().getContextClassLoader, Array.empty)
      ThreadUtils.withThisAsContextClassLoader[Unit](flinkLoaderSimulation)(action)
    } else {
      action
    }
  }

  //When serializing process graph (StateDescriptor:233) KryoSerializer is initialized without env configuration
  //Maybe it's a bug in flink??
  //TODO: is it the only place where we should do it??
  private def initializeStateDescriptors(env: StreamExecutionEnvironment): Unit = {
    val config = env.getConfig
    env.getStreamGraph.getOperators.toSet[tuple.Tuple2[Integer, StreamOperator[_]]].map(_.f1).collect {
      case window:WindowOperator[_, _, _, _, _] => window.getStateDescriptor.initializeSerializerUnlessSet(config)
    }
  }

  private def register(env: StreamExecutionEnvironment, compiledProcessWithDeps: (ClassLoader) => CompiledProcessWithDeps,
                       testRunId: Option[TestRunId]): Unit = {


    //here we are sure the classloader is ok
    val processWithDeps = compiledProcessWithDeps(UserClassLoader.get("root"))
    val metaData = processWithDeps.metaData

    val streamMetaData = MetaDataExtractor.extractStreamMetaDataOrFail(metaData)
    env.setRestartStrategy(processWithDeps.exceptionHandler.restartStrategy)
    streamMetaData.parallelism.foreach(env.setParallelism)

    configureCheckpoints(env, streamMetaData)

    val asyncExecutionContextPreparer = processWithDeps.asyncExecutionContextPreparer

    diskStateBackend match {
      case Some(backend) if streamMetaData.splitStateToDisk.getOrElse(false) =>
        logger.info("Using disk state backend")
        env.setStateBackend(backend)
      case _ => logger.info("Using default state backend")
    }

    registerSourcePart(processWithDeps.source)

    def registerSourcePart(part: SourcePart): Unit = {
      //TODO: get rid of cast (but how??)
      val source = part.obj.asInstanceOf[FlinkSource[Any]]

      val timestampAssigner = source.timestampAssigner

      env.setStreamTimeCharacteristic(if (timestampAssigner.isDefined) TimeCharacteristic.EventTime else TimeCharacteristic.IngestionTime)

      val newStart = env
        .addSource[Any](source.toFlinkSource)(source.typeInformation)
        .name(s"${metaData.id}-source")
      val withAssigned = timestampAssigner.map {
        case periodic: AssignerWithPeriodicWatermarks[Any@unchecked] =>
          newStart.assignTimestampsAndWatermarks(periodic)
        case punctuated: AssignerWithPunctuatedWatermarks[Any@unchecked] =>
          newStart.assignTimestampsAndWatermarks(punctuated)
      }.map(_.transform("even-time-meter", new EventTimeDelayMeterFunction("eventtimedelay", eventTimeMetricDuration)))
        .getOrElse(newStart)
        .map(new RateMeterFunction[Any]("source"))
          .map(InitContextFunction(metaData.id, part.node.id))

      val asyncAssigned = wrapAsync(withAssigned, part.node, part.validationContext, "interpretation").split(SplitFunction)

      registerParts(asyncAssigned, part.nextParts, part.ends)
    }

    def registerParts(start: SplitStream[InterpretationResult],
                      nextParts: Seq[SubsequentPart],
                      ends: Seq[End]) : Unit = {
      nextParts.foreach { part =>
        registerSubsequentPart(start.select(part.id), part)
      }
      start.select(EndId)
        .addSink(new EndRateMeterFunction(ends))
    }

    def registerSubsequentPart[T](start: DataStream[InterpretationResult],
                                  processPart: SubsequentPart): Unit =
      processPart match {

        case part@SinkPart(sink: FlinkSink, sinkDef, validationContext) => {
          val startAfterSinkEvaluated = wrapAsync(start.map(_.finalContext), part.node, validationContext, "function")
            .map(new EndRateMeterFunction(part.ends))

          val disabled = sinkDef.data.isDisabled.contains(true)

          val withSinkAdded = if (disabled) {
            startAfterSinkEvaluated.map(_.output).addSink(EmptySink.toFlinkFunction)
          } else {
            //TODO: maybe this logic should be moved to compiler instead?
            testRunId match {
              case None =>
                startAfterSinkEvaluated
                  .map(_.output)
                  .addSink(sink.toFlinkFunction)
              case Some(runId) =>
                val typ = part.node.data.ref.typ
                val prepareFunction = sink.testDataOutput.getOrElse(throw new IllegalArgumentException(s"Sink $typ cannot be mocked"))
                val collectingSink = SinkInvocationCollector(runId, part.id, typ, prepareFunction)
                startAfterSinkEvaluated.addSink(new CollectingSinkFunction(compiledProcessWithDeps, collectingSink, part))
            }
          }
          withSinkAdded.name(s"${metaData.id}-${part.id}-sink")
        }
        case part:SinkPart =>
          throw new IllegalArgumentException(s"Process can only use flink sinks, instead given: ${part.obj}")
        case SplitPart(splitNode, validationContext, nexts) =>
          val nextIds = nexts.map(_.next.id)
          //TODO: there is bug in flink - if there are 2 splits in a row, without map
          //TODO: this is only place where we count outside interpreter - is it really needed?
          val newStart = (testRunId match {
            case None =>
              start.map(NodeCountMetricFunction[InterpretationResult](splitNode.id))
            case Some(runId) =>
              val splitCollector = SplitInvocationCollector(runId, splitNode.id)
              start.map(value => {
                splitCollector.collect(value)
                value
              })
          }).split(_ => nextIds)
          nexts.foreach {
            //TODO: is this part really ok && needed?
            case NextWithParts(NextNode(nextNode), parts, ends) =>
              val beforeAsync = newStart.select(nextNode.id).map(_.finalContext)
              val interpreted = wrapAsync(beforeAsync, nextNode, validationContext, "interpretation")
                  .split(SplitFunction)
              registerParts(interpreted, parts, ends)
            case NextWithParts(PartRef(id), parts, ends) =>
              val splitted = newStart.select(id).split(SplitFunction)
              registerParts(splitted, parts, ends)
          }
        case part@CustomNodePart(executor:
          CustomNodeInvoker[FlinkCustomStreamTransformation@unchecked],
              node, validationContext, nextValidationContext, nextParts, ends) =>

          val newContextFun = (ir: ValueWithContext[_]) => node.data.outputVar match {
            case Some(name) => ir.context.withVariable(name, ir.value)
            case None => ir.context
          }

          val customNodeContext = FlinkCustomNodeContext(metaData,
            node.id, processWithDeps.processTimeout, (classLoader) => compiledProcessWithDeps(classLoader).exceptionHandler, processWithDeps.signalSenders)
          val newStart = executor.run(() => compiledProcessWithDeps(UserClassLoader.get(node.id)).customNodeInvokerDeps).transform(start, customNodeContext)
              .map(newContextFun)
          val afterSplit = wrapAsync(newStart, node, validationContext, "customNodeInterpretation", Some(nextValidationContext))
              .split(SplitFunction)

          registerParts(afterSplit, nextParts, ends)
        case e:CustomNodePart =>
          throw new IllegalArgumentException(s"Unknown CustomNodeExecutor: ${e.customNodeInvoker}")
      }

    def wrapAsync(beforeAsync: DataStream[Context], node: SplittedNode[_], validationContext: ValidationContext, name: String, nextValidationContext: Option[ValidationContext] = None) : DataStream[InterpretationResult] = {
      if (streamMetaData.shouldUseAsyncInterpretation) {
        val asyncFunction = new AsyncInterpretationFunction(compiledProcessWithDeps, node, validationContext, nextValidationContext, asyncExecutionContextPreparer)
        new DataStream(datastream.AsyncDataStream.orderedWait(beforeAsync.javaStream, asyncFunction,
          processWithDeps.processTimeout.toMillis, TimeUnit.MILLISECONDS, asyncExecutionContextPreparer.bufferSize))
      } else {
        beforeAsync.flatMap(new SyncInterpretationFunction(compiledProcessWithDeps, node, validationContext, nextValidationContext))
      }.name(s"${metaData.id}-${node.id}-$name")
    }
  }

  private def configureCheckpoints(env: StreamExecutionEnvironment, streamMetaData: StreamMetaData): Unit = {
    val checkpointIntervalToSetInMillis = streamMetaData.checkpointIntervalDuration.getOrElse(checkpointInterval).toMillis
    env.enableCheckpointing(checkpointIntervalToSetInMillis)

    //TODO: should this be configurable?
    env.getCheckpointConfig.setMinPauseBetweenCheckpoints(checkpointIntervalToSetInMillis / 2)
    env.getCheckpointConfig.setMaxConcurrentCheckpoints(1)
  }
}


object FlinkProcessRegistrar {

  import net.ceedubs.ficus.Ficus._
  import net.ceedubs.ficus.readers.ArbitraryTypeReader._
  import net.ceedubs.ficus.readers.ValueReader

  private final val EndId = "$end"

  def apply(compiler: FlinkProcessCompiler, config: Config) : FlinkProcessRegistrar = {

    val enableObjectReuse = config.getOrElse[Boolean]("enableObjectReuse", true)
    val eventTimeMetricDuration = config.getOrElse[FiniteDuration]("eventTimeMetricSlideDuration", 10.seconds)
    val checkpointInterval = config.as[FiniteDuration]("checkpointInterval")

    new FlinkProcessRegistrar(
      compileProcess = compiler.compileProcess,
      eventTimeMetricDuration = eventTimeMetricDuration,
      checkpointInterval = checkpointInterval,
      enableObjectReuse = enableObjectReuse,
      diskStateBackend =  {
        if (compiler.diskStateBackendSupport) {
          config.getAs[RocksDBStateBackendConfig]("rocksDB").map(StateConfiguration.prepareRocksDBStateBackend)
        } else None
      }
    )
  }



  class SyncInterpretationFunction(val compiledProcessWithDepsProvider: (ClassLoader) => CompiledProcessWithDeps,
                                   node: SplittedNode[_], validationContext: ValidationContext, nextValidationContext: Option[ValidationContext])
    extends RichFlatMapFunction[Context, InterpretationResult] with WithCompiledProcessDeps {

    private lazy implicit val ec = SynchronousExecutionContext.ctx
    private lazy val compiledNode = compiledProcessWithDeps.compileSubPart(node, validationContext, nextValidationContext)
    import compiledProcessWithDeps._

    override def flatMap(input: Context, collector: Collector[InterpretationResult]): Unit = {
      (try {
        Await.result(interpreter.interpret(compiledNode, metaData, input), processTimeout)
      } catch {
        case NonFatal(error) => Right(EspExceptionInfo(None, error, input))
      }) match {
        case Left(ir) =>
          exceptionHandler.handling(None, input)(collector.collect(ir))
        case Right(info) =>
          exceptionHandler.handle(info)
      }
    }

  }

  class AsyncInterpretationFunction(val compiledProcessWithDepsProvider: (ClassLoader) => CompiledProcessWithDeps,
                                    node: SplittedNode[_], validationContext: ValidationContext, nextValidationContext: Option[ValidationContext], asyncExecutionContextPreparer: AsyncExecutionContextPreparer)
    extends RichAsyncFunction[Context, InterpretationResult] with LazyLogging with WithCompiledProcessDeps {


    private lazy val compiledNode = compiledProcessWithDeps.compileSubPart(node, validationContext, nextValidationContext)
    import compiledProcessWithDeps._

    private var executionContext : ExecutionContext = _

    override def open(parameters: Configuration): Unit = {
      super.open(parameters)
      executionContext = asyncExecutionContextPreparer.prepareExecutionContext(compiledProcessWithDeps.metaData.id,
        getRuntimeContext.getExecutionConfig.getParallelism)
    }


    private def handleException(collector: ResultFuture[InterpretationResult], info: EspExceptionInfo[_<:Throwable]): Unit = {
      try {
        exceptionHandler.handle(info)
        collector.complete(Collections.emptyList[InterpretationResult]())
      } catch {
        case NonFatal(e) => logger.warn("Unexpected fail, refusing to collect??", e); collector.completeExceptionally(e)
      }
    }

    override def asyncInvoke(input: Context, collector: ResultFuture[InterpretationResult]) : Unit = {
      implicit val ec = executionContext
      try {
        interpreter.interpret(compiledNode, metaData, input)
          .onComplete {
            case Success(Left(result)) => collector.complete(Collections.singletonList[InterpretationResult](result))
            case Success(Right(exInfo)) => handleException(collector, exInfo)
            case Failure(ex) =>
              logger.warn("Unexpected error", ex)
              handleException(collector, EspExceptionInfo(None, ex, input))
          }
      } catch {
        case NonFatal(ex) =>
          logger.warn("Unexpected error", ex)
          handleException(collector, EspExceptionInfo(None, ex, input))
      }
    }

    override def close(): Unit = {
      super.close()
      asyncExecutionContextPreparer.close()
    }

  }

  class CollectingSinkFunction(val compiledProcessWithDepsProvider: (ClassLoader) => CompiledProcessWithDeps,
                               collectingSink: SinkInvocationCollector, sink: SinkPart)
    extends RichSinkFunction[InterpretationResult] with WithCompiledProcessDeps {

    override def invoke(value: InterpretationResult) = {
      compiledProcessWithDeps.exceptionHandler.handling(Some(sink.id), value.finalContext) {
        collectingSink.collect(value)
      }
    }
  }

  class RateMeterFunction[T](groupId: String) extends RichMapFunction[T, T] {
    private var instantRateMeter : RateMeter = _

    override def open(parameters: Configuration): Unit = {
      super.open(parameters)

      instantRateMeter = InstantRateMeterWithCount.register(getRuntimeContext.getMetricGroup.addGroup(groupId))
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

  case class NodeCountMetricFunction[T](nodeId: String) extends RichMapFunction[T, T] {

    private var counter : Counter = _

    override def open(parameters: Configuration): Unit = {
      super.open(parameters)
      counter = getRuntimeContext.getMetricGroup.addGroup("nodeCount").counter(nodeId)
    }

    override def map(value: T): T = {
      counter.inc()
      value
    }
  }

  class EventTimeDelayMeterFunction[T](groupId: String, slidingWindow: FiniteDuration)
    extends AbstractStreamOperator[T] with OneInputStreamOperator[T, T] {

    setChainingStrategy(ChainingStrategy.ALWAYS)

    lazy val histogramMeter = new DropwizardHistogramWrapper(
      new Histogram(
        new SlidingTimeWindowReservoir(slidingWindow.toMillis, TimeUnit.MILLISECONDS)))

    lazy val minimalDelayGauge = new Gauge[Long] {
      override def getValue = {
        val now = System.currentTimeMillis()
        now - lastElement.getOrElse(now)
      }
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

  class EndRateMeterFunction(ends: Seq[End]) extends AbstractRichFunction
    with MapFunction[InterpretationResult, InterpretationResult] with SinkFunction[InterpretationResult] {

    @transient private var meterByReference: Map[PartReference, RateMeter] = _

    override def open(parameters: Configuration): Unit = {
      super.open(parameters)

      val parentGroupForNormalEnds = getRuntimeContext.getMetricGroup.addGroup("end")
      val parentGroupForDeadEnds = getRuntimeContext.getMetricGroup.addGroup("dead_end")

      def registerRateMeter(end: End) = {
        val baseGroup = end match {
          case normal: NormalEnd => parentGroupForNormalEnds
          case dead: DeadEnd => parentGroupForDeadEnds
        }
        InstantRateMeterWithCount.register(baseGroup.addGroup(end.nodeId))
      }

      meterByReference = ends.map { end =>
        val reference = end match {
          case NormalEnd(nodeId) =>  EndReference(nodeId)
          case DeadEnd(nodeId) =>  DeadEndReference(nodeId)
        }
        reference -> registerRateMeter(end)
      }.toMap[PartReference, RateMeter]
    }

    override def map(value: InterpretationResult) = {
      val meter = meterByReference.getOrElse(value.reference, throw new IllegalArgumentException("Unexpected reference: " + value.reference))
      meter.mark()
      value
    }

    override def invoke(value: InterpretationResult) = {
      map(value)
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