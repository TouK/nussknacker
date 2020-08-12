package pl.touk.nussknacker.engine.process

import java.util.Collections
import java.util.concurrent.TimeUnit

import cats.data.NonEmptyList
import com.codahale.metrics.{Histogram, SlidingTimeWindowReservoir}
import com.typesafe.config.Config
import com.typesafe.scalalogging.{LazyLogging, Logger}
import org.apache.flink.api.common.ExecutionConfig
import org.apache.flink.api.common.functions._
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.api.java.tuple
import org.apache.flink.configuration.Configuration
import org.apache.flink.dropwizard.metrics.DropwizardHistogramWrapper
import org.apache.flink.metrics.Gauge
import org.apache.flink.runtime.state.StateBackend
import org.apache.flink.streaming.api.environment.RemoteStreamEnvironment
import org.apache.flink.streaming.api.functions.ProcessFunction
import org.apache.flink.streaming.api.functions.async.{ResultFuture, RichAsyncFunction}
import org.apache.flink.streaming.api.functions.sink.{RichSinkFunction, SinkFunction}
import org.apache.flink.streaming.api.operators.StreamOperatorFactory
import org.apache.flink.streaming.api.scala.{StreamExecutionEnvironment, _}
import org.apache.flink.streaming.api.windowing.time.Time
import org.apache.flink.streaming.runtime.operators.windowing.WindowOperator
import org.apache.flink.util.Collector
import org.slf4j.LoggerFactory
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.async.{DefaultAsyncInterpretationValue, DefaultAsyncInterpretationValueDeterminer}
import pl.touk.nussknacker.engine.api.context.{ContextTransformation, JoinContextTransformation, ValidationContext}
import pl.touk.nussknacker.engine.api.exception.EspExceptionInfo
import pl.touk.nussknacker.engine.api.process.AsyncExecutionContextPreparer
import pl.touk.nussknacker.engine.api.test.InvocationCollectors.SinkInvocationCollector
import pl.touk.nussknacker.engine.api.test.TestRunId
import pl.touk.nussknacker.engine.compiledgraph.part._
import pl.touk.nussknacker.engine.definition.{CompilerLazyParameterInterpreter, LazyInterpreterDependencies}
import pl.touk.nussknacker.engine.flink.api.NkGlobalParameters
import pl.touk.nussknacker.engine.flink.api.compat.ExplicitUidInOperatorsSupport
import pl.touk.nussknacker.engine.flink.api.process.{FlinkCustomJoinTransformation, _}
import pl.touk.nussknacker.engine.flink.util.metrics.{InstantRateMeterWithCount, MetricUtils}
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.graph.node.BranchEndDefinition
import pl.touk.nussknacker.engine.process.FlinkStreamingProcessRegistrar._
import pl.touk.nussknacker.engine.process.compiler.{CompiledProcessWithDeps, FlinkProcessCompiler}
import pl.touk.nussknacker.engine.process.util.StateConfiguration.RocksDBStateBackendConfig
import pl.touk.nussknacker.engine.process.util.{MetaDataExtractor, Serializers, StateConfiguration, UserClassLoader}
import pl.touk.nussknacker.engine.splittedgraph.end.{BranchEnd, DeadEnd, End, NormalEnd}
import pl.touk.nussknacker.engine.splittedgraph.splittednode.SplittedNode
import pl.touk.nussknacker.engine.util.metrics.RateMeter
import shapeless.syntax.typeable._

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.language.implicitConversions
import scala.util.control.NonFatal
import scala.util.{Failure, Success}

class FlinkStreamingProcessRegistrar(compileProcess: (EspProcess, ProcessVersion) => ClassLoader => CompiledProcessWithDeps,
                                     eventTimeMetricDuration: FiniteDuration,
                                     checkpointConfig: Option[CheckpointConfig],
                                     diskStateBackend: Option[StateBackend],
                                     executionConfigPreparer: ExecutionConfigPreparer)
  extends FlinkProcessRegistrar[StreamExecutionEnvironment] with LazyLogging {

  import FlinkProcessRegistrar._

  implicit def millisToTime(duration: Long): Time = Time.of(duration, TimeUnit.MILLISECONDS)

  override protected def isRemoteEnv(env: StreamExecutionEnvironment): Boolean = env.getJavaEnv.isInstanceOf[RemoteStreamEnvironment]

  def register(env: StreamExecutionEnvironment, process: EspProcess, processVersion: ProcessVersion, testRunId: Option[TestRunId] = None): Unit = {
    usingRightClassloader(env) {
      executionConfigPreparer.prepareExecutionConfig(env.getConfig)(process, processVersion)
      register(env, compileProcess(process, processVersion), testRunId)
    }
    initializeStateDescriptors(env)
  }

  //TODO: check if it's still valid in Flink 1.9
  //When serializing process graph (StateDescriptor:233) KryoSerializer is initialized without env configuration
  //Maybe it's a bug in flink??
  private def initializeStateDescriptors(env: StreamExecutionEnvironment): Unit = {
    val config = env.getConfig
    env.getStreamGraph("", clearTransformations = false).getAllOperatorFactory.asScala.toSet[tuple.Tuple2[Integer, StreamOperatorFactory[_]]].map(_.f1).collect {
      case window:WindowOperator[_, _, _, _, _] => window.getStateDescriptor.initializeSerializerUnlessSet(config)
    }
  }

  private def register(env: StreamExecutionEnvironment, compiledProcessWithDeps: ClassLoader => CompiledProcessWithDeps,
                       testRunId: Option[TestRunId]): Unit = {


    //here we are sure the classloader is ok
    val processWithDeps = compiledProcessWithDeps(UserClassLoader.get("root"))
    val metaData = processWithDeps.metaData
    val globalParameters = NkGlobalParameters.readFromContext(env.getConfig)

    def nodeContext(nodeId: String): FlinkCustomNodeContext = {
      FlinkCustomNodeContext(metaData, nodeId, processWithDeps.processTimeout,
        new FlinkLazyParameterFunctionHelper(createInterpreter(compiledProcessWithDeps)),
        processWithDeps.signalSenders, globalParameters)
    }

    val streamMetaData = MetaDataExtractor.extractTypeSpecificDataOrFail[StreamMetaData](metaData)
    env.setRestartStrategy(processWithDeps.exceptionHandler.restartStrategy)
    streamMetaData.parallelism.foreach(env.setParallelism)

    configureCheckpoints(env, streamMetaData)

    val asyncExecutionContextPreparer = processWithDeps.asyncExecutionContextPreparer
    implicit val defaultAsync: DefaultAsyncInterpretationValue = DefaultAsyncInterpretationValueDeterminer.determine(asyncExecutionContextPreparer)

    diskStateBackend match {
      case Some(backend) if streamMetaData.splitStateToDisk.getOrElse(false) =>
        logger.debug("Using disk state backend")
        env.setStateBackend(backend)
      case _ => logger.debug("Using default state backend")
    }

    {
      //it is *very* important that source are in correct order here - see ProcessCompiler.compileSources comments
      processWithDeps.sources.toList.foldLeft(Map.empty[BranchEndDefinition, DataStream[InterpretationResult]]) {
        case (branchEnds, next:SourcePart) => branchEnds ++ registerSourcePart(next)
        case (branchEnds, joinPart:CustomNodePart) => branchEnds ++ registerJoinPart(joinPart, branchEnds)
      }
    }

    //thanks to correct sorting, we know that branchEnds contain all edges to joinPart
    def registerJoinPart(joinPart: CustomNodePart, branchEnds: Map[BranchEndDefinition, DataStream[InterpretationResult]]): Map[BranchEndDefinition, DataStream[InterpretationResult]] = {
      val inputs: Map[String, DataStream[Context]] = branchEnds.collect {
        case (BranchEndDefinition(id, joinId), stream) if joinPart.id == joinId => id -> stream.map(_.finalContext)
      }

      val transformer = joinPart.transformer match {
        case joinTransformer: FlinkCustomJoinTransformation => joinTransformer
        case JoinContextTransformation(_, impl: FlinkCustomJoinTransformation) => impl
        case other =>
          throw new IllegalArgumentException(s"Unknown join node transformer: $other")
      }

      val outputVar = joinPart.node.data.outputVar.get
      val newContextFun = (ir: ValueWithContext[_]) => ir.context.withVariable(outputVar, ir.value)

      val newStart = transformer.transform(inputs, nodeContext(joinPart.id)).map(newContextFun)

      val afterSplit = wrapAsync(newStart, joinPart.node, joinPart.validationContext, globalParameters, "branchInterpretation")
      registerNextParts(afterSplit, joinPart)
    }

    def registerSourcePart(part: SourcePart): Map[BranchEndDefinition, DataStream[InterpretationResult]] = {
      //TODO: get rid of cast (but how??)
      val source = part.obj.asInstanceOf[FlinkSource[Any]]

      val start = source
          .sourceStream(env, nodeContext(part.id))
          .process(new EventTimeDelayMeterFunction("eventtimedelay", part.id, eventTimeMetricDuration))
          .map(new RateMeterFunction[Any]("source", part.id))
          .map(InitContextFunction(metaData.id, part.id))

      val asyncAssigned = wrapAsync(start, part.node, part.validationContext, globalParameters, "interpretation")

      registerNextParts(asyncAssigned, part)
    }

    //the method returns all possible branch ends in part, together with DataStream leading to them
    def registerNextParts(start: DataStream[Unit], part: PotentiallyStartPart) : Map[BranchEndDefinition, DataStream[InterpretationResult]] = {
      val ends = part.ends
      val nextParts = part.nextParts

      start.getSideOutput(OutputTag[InterpretationResult](EndId))
        .addSink(new EndRateMeterFunction(ends))

      val branchesForParts = nextParts.map { part =>
        registerSubsequentPart(start.getSideOutput(OutputTag[InterpretationResult](part.id)), part)
      }.foldLeft(Map[BranchEndDefinition, DataStream[InterpretationResult]]()){_ ++ _}
      val branchForEnds = part.ends.flatMap(_.cast[BranchEnd]).map(be =>  be.definition ->
        start.getSideOutput(OutputTag[InterpretationResult](be.nodeId))).toMap
      branchesForParts ++ branchForEnds
    }

    def registerSubsequentPart[T](start: DataStream[InterpretationResult],
                                  processPart: SubsequentPart): Map[BranchEndDefinition, DataStream[InterpretationResult]] =
      processPart match {

        case part@SinkPart(sink: FlinkSink, _, validationContext) =>
          val startAfterSinkEvaluated = wrapAsync(start.map(_.finalContext), part.node, validationContext, globalParameters, "function")
            .getSideOutput(OutputTag[InterpretationResult](EndId))
            .map(new EndRateMeterFunction(part.ends))

          val withSinkAdded =
            //TODO: maybe this logic should be moved to compiler instead?
            testRunId match {
              case None =>
                sink.registerSink(startAfterSinkEvaluated, nodeContext(part.id))
              case Some(runId) =>
                val typ = part.node.data.ref.typ
                val prepareFunction = sink.testDataOutput.getOrElse(throw new IllegalArgumentException(s"Sink $typ cannot be mocked"))
                val collectingSink = SinkInvocationCollector(runId, part.id, typ, prepareFunction)
                startAfterSinkEvaluated.addSink(new CollectingSinkFunction(compiledProcessWithDeps, collectingSink, part))
            }

          withSinkAdded.name(s"${metaData.id}-${part.id}-sink")
          Map()

        case part:SinkPart =>
          throw new IllegalArgumentException(s"Process can only use flink sinks, instead given: ${part.obj}")
        case part@CustomNodePart(transformerObj, node, validationContext, _, _) =>

          val transformer = transformerObj match {
            case t: FlinkCustomStreamTransformation => t
            case ContextTransformation(_, impl: FlinkCustomStreamTransformation) => impl
            case other =>
              throw new IllegalArgumentException(s"Unknown custom node transformer: $other")
          }

          val newContextFun = (ir: ValueWithContext[_]) => node.data.outputVar match {
            case Some(name) => ir.context.withVariable(name, ir.value)
            case None => ir.context
          }

          val customNodeContext = nodeContext(part.id)
          val newStart = transformer.transform(start.map(_.finalContext), customNodeContext)
              .map(newContextFun)
          val afterSplit = wrapAsync(newStart, node, validationContext, globalParameters, "customNodeInterpretation")

          registerNextParts(afterSplit, part)
      }

    def wrapAsync(beforeAsync: DataStream[Context], node: SplittedNode[_], validationContext: ValidationContext, globalParameters: Option[NkGlobalParameters], name: String) : DataStream[Unit] = {
      (if (streamMetaData.shouldUseAsyncInterpretation) {
        val asyncFunction = new AsyncInterpretationFunction(compiledProcessWithDeps, node, validationContext, asyncExecutionContextPreparer)
        ExplicitUidInOperatorsSupport.setUidIfNeed(ExplicitUidInOperatorsSupport.defaultExplicitUidInStatefulOperators(globalParameters), node.id + "-$async")(
          new DataStream(org.apache.flink.streaming.api.datastream.AsyncDataStream.orderedWait(beforeAsync.javaStream, asyncFunction,
            processWithDeps.processTimeout.toMillis, TimeUnit.MILLISECONDS, asyncExecutionContextPreparer.bufferSize)))
      } else {
        beforeAsync.flatMap(new SyncInterpretationFunction(compiledProcessWithDeps, node, validationContext))
      }).name(s"${metaData.id}-${node.id}-$name").process(new SplitFunction)
    }
  }

  private def configureCheckpoints(env: StreamExecutionEnvironment, streamMetaData: StreamMetaData): Unit = {
    val processSpecificCheckpointIntervalDuration = streamMetaData.checkpointIntervalDuration
    val checkpointIntervalToSet = processSpecificCheckpointIntervalDuration.orElse(checkpointConfig.map(_.checkpointInterval)).map(_.toMillis)
    checkpointIntervalToSet.foreach { checkpointIntervalToSetInMillis =>
      env.enableCheckpointing(checkpointIntervalToSetInMillis)
      env.getCheckpointConfig.setMinPauseBetweenCheckpoints(checkpointConfig.flatMap(_.minPauseBetweenCheckpoints).map(_.toMillis).getOrElse(checkpointIntervalToSetInMillis / 2))
      env.getCheckpointConfig.setMaxConcurrentCheckpoints(checkpointConfig.flatMap(_.maxConcurrentCheckpoints).getOrElse(1))
      checkpointConfig.flatMap(_.tolerableCheckpointFailureNumber).foreach(env.getCheckpointConfig.setTolerableCheckpointFailureNumber)
    }
  }

}


object FlinkStreamingProcessRegistrar {

  // We cannot use LazyLogging trait here because class already has LazyLogging and scala ends with cycle during resolution...
  private lazy val logger: Logger = Logger(LoggerFactory.getLogger(classOf[FlinkStreamingProcessRegistrar].getName))

  import net.ceedubs.ficus.Ficus._
  import net.ceedubs.ficus.readers.ArbitraryTypeReader._

  private final val EndId = "$end"

  def apply(compiler: FlinkProcessCompiler, config: Config, prepareExecutionConfig: ExecutionConfigPreparer) : FlinkStreamingProcessRegistrar = {
    val eventTimeMetricDuration = config.getOrElse[FiniteDuration]("eventTimeMetricSlideDuration", 10.seconds)

    // TODO checkpointInterval is deprecated - remove it in future
    val checkpointInterval = config.getAs[FiniteDuration](path = "checkpointInterval")
    if (checkpointInterval.isDefined) {
      logger.warn("checkpointInterval config property is deprecated, use checkpointConfig.checkpointInterval instead")
    }

    val checkpointConfig = config.getAs[CheckpointConfig](path = "checkpointConfig")
      .orElse(checkpointInterval.map(CheckpointConfig(_)))

    new FlinkStreamingProcessRegistrar(
      compileProcess = compiler.compileProcess,
      eventTimeMetricDuration = eventTimeMetricDuration,
      diskStateBackend =  {
        if (compiler.diskStateBackendSupport) {
          config.getAs[RocksDBStateBackendConfig]("rocksDB").map(StateConfiguration.prepareRocksDBStateBackend)
        } else None
      },
      checkpointConfig = checkpointConfig,
      executionConfigPreparer = prepareExecutionConfig
    )
  }

  private def createInterpreter(compiledProcessWithDepsProvider: ClassLoader => CompiledProcessWithDeps) =
                               (runtimeContext: RuntimeContext) =>
    new FlinkCompilerLazyInterpreterCreator(runtimeContext, compiledProcessWithDepsProvider(runtimeContext.getUserCodeClassLoader))

  class AsyncInterpretationFunction(val compiledProcessWithDepsProvider: ClassLoader => CompiledProcessWithDeps,
                                    node: SplittedNode[_], validationContext: ValidationContext, asyncExecutionContextPreparer: AsyncExecutionContextPreparer)
    extends RichAsyncFunction[Context, InterpretationResult] with LazyLogging with WithCompiledProcessDeps {


    private lazy val compiledNode = compiledProcessWithDeps.compileSubPart(node, validationContext)
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
      implicit val ec: ExecutionContext = executionContext
      try {
        interpreter.interpret(compiledNode, metaData, input)
          .onComplete {
            case Success(Left(result)) => collector.complete(result.asJava)
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

  class CollectingSinkFunction(val compiledProcessWithDepsProvider: ClassLoader => CompiledProcessWithDeps,
                               collectingSink: SinkInvocationCollector, sink: SinkPart)
    extends RichSinkFunction[InterpretationResult] with WithCompiledProcessDeps {


    override def invoke(value: InterpretationResult, context: SinkFunction.Context[_]): Unit = {
      compiledProcessWithDeps.exceptionHandler.handling(Some(sink.id), value.finalContext) {
        collectingSink.collect(value)
      }
    }
  }

  class EventTimeDelayMeterFunction[T](groupId: String, nodeId: String, slidingWindow: FiniteDuration) extends ProcessFunction[T, T] {

    lazy val histogramMeter = new DropwizardHistogramWrapper(
      new Histogram(new SlidingTimeWindowReservoir(slidingWindow.toMillis, TimeUnit.MILLISECONDS)))

    lazy val minimalDelayGauge: Gauge[Long] = new Gauge[Long] {
      override def getValue: Long = {
        val now = System.currentTimeMillis()
        now - lastElementTime.getOrElse(now)
      }
    }

    var lastElementTime : Option[Long] = None

    override def open(parameters: Configuration): Unit = {
      val metrics = new MetricUtils(getRuntimeContext)
      metrics.histogram(NonEmptyList.of(groupId, "histogram"), Map("nodeId" -> nodeId), histogramMeter)
      metrics.gauge[Long, Gauge[Long]](NonEmptyList.of(groupId, "minimalDelay"), Map("nodeId" -> nodeId), minimalDelayGauge)
    }

    override def processElement(value: T, ctx: ProcessFunction[T, T]#Context, out: Collector[T]): Unit = {
      Option(ctx.timestamp()).foreach { timestamp =>
        val delay = System.currentTimeMillis() - timestamp
        histogramMeter.update(delay)
        lastElementTime = Some(lastElementTime.fold(timestamp)(math.max(_, timestamp)))
      }
      out.collect(value)
    }

  }

  class EndRateMeterFunction(ends: Seq[End]) extends AbstractRichFunction
    with MapFunction[InterpretationResult, InterpretationResult] with SinkFunction[InterpretationResult] {

    @transient private var meterByReference: Map[PartReference, RateMeter] = _

    override def open(parameters: Configuration): Unit = {
      super.open(parameters)

      val parentGroupForNormalEnds = "end"
      val parentGroupForDeadEnds = "dead_end"

      def registerRateMeter(end: End): InstantRateMeterWithCount = {
        val baseGroup = end match {
          case _: NormalEnd => parentGroupForNormalEnds
          case _: DeadEnd => parentGroupForDeadEnds
          case _: BranchEnd => parentGroupForDeadEnds
        }
        InstantRateMeterWithCount.register(Map("nodeId" -> end.nodeId), List(baseGroup), new MetricUtils(getRuntimeContext))
      }

      meterByReference = ends.map { end =>
        val reference = end match {
          case NormalEnd(nodeId) =>  EndReference(nodeId)
          case DeadEnd(nodeId) =>  DeadEndReference(nodeId)
          case BranchEnd(definition) => definition.joinReference
        }
        reference -> registerRateMeter(end)
      }.toMap[PartReference, RateMeter]
    }

    override def map(value: InterpretationResult): InterpretationResult = {
      val meter = meterByReference.getOrElse(value.reference, throw new IllegalArgumentException("Unexpected reference: " + value.reference))
      meter.mark()
      value
    }

    override def invoke(value: InterpretationResult, context: SinkFunction.Context[_]): Unit = {
      map(value)
    }
  }

  class SplitFunction extends ProcessFunction[InterpretationResult, Unit] {

    //we eagerly create TypeInformation here, creating it during OutputTag construction would be too expensive
    private lazy val typeInfo: TypeInformation[InterpretationResult] = implicitly[TypeInformation[InterpretationResult]]

    override def processElement(interpretationResult: InterpretationResult, ctx: ProcessFunction[InterpretationResult, Unit]#Context,
                                out: Collector[Unit]): Unit = {
      val tagName = interpretationResult.reference match {
        case NextPartReference(id) => id
        //TODO JOIN - this is a bit weird, probably refactoring of splitted process structures will help...
        case JoinReference(id, _) => id
        case _: EndingReference => EndId
      }
      ctx.output(OutputTag[InterpretationResult](tagName)(typeInfo), interpretationResult)
    }
  }

}

class FlinkCompilerLazyInterpreterCreator(runtimeContext: RuntimeContext, withDeps: CompiledProcessWithDeps)
  extends CompilerLazyParameterInterpreter {

  //TODO: is this good place?
  withDeps.open(runtimeContext)

  val deps: LazyInterpreterDependencies = withDeps.lazyInterpreterDeps

  val metaData: MetaData = withDeps.metaData

  override def close(): Unit = {
    withDeps.close()
  }
}
