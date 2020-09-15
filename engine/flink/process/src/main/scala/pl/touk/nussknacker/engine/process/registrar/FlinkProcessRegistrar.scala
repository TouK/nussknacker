package pl.touk.nussknacker.engine.process.registrar

import java.util.concurrent.TimeUnit

import com.typesafe.config.Config
import com.typesafe.scalalogging.{LazyLogging, Logger}
import org.apache.flink.api.common.functions.RuntimeContext
import org.apache.flink.streaming.api.environment.RemoteStreamEnvironment
import org.apache.flink.streaming.api.scala.{DataStream, StreamExecutionEnvironment, _}
import org.apache.flink.streaming.api.windowing.time.Time
import org.slf4j.LoggerFactory
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.async.{DefaultAsyncInterpretationValue, DefaultAsyncInterpretationValueDeterminer}
import pl.touk.nussknacker.engine.api.context.{ContextTransformation, JoinContextTransformation, ValidationContext}
import pl.touk.nussknacker.engine.api.test.InvocationCollectors.SinkInvocationCollector
import pl.touk.nussknacker.engine.api.test.TestRunId
import pl.touk.nussknacker.engine.compiledgraph.part._
import pl.touk.nussknacker.engine.flink.api.NkGlobalParameters
import pl.touk.nussknacker.engine.flink.api.compat.ExplicitUidInOperatorsSupport
import pl.touk.nussknacker.engine.flink.api.process.{FlinkCustomJoinTransformation, _}
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.graph.node.BranchEndDefinition
import pl.touk.nussknacker.engine.process.compiler.{CompiledProcessWithDeps, FlinkProcessCompiler}
import pl.touk.nussknacker.engine.process.util.StateConfiguration.RocksDBStateBackendConfig
import pl.touk.nussknacker.engine.process.util.{MetaDataExtractor, UserClassLoader}
import pl.touk.nussknacker.engine.process.{CheckpointConfig, ExecutionConfigPreparer, FlinkCompatibilityProvider}
import pl.touk.nussknacker.engine.splittedgraph.end.BranchEnd
import pl.touk.nussknacker.engine.splittedgraph.splittednode.SplittedNode
import pl.touk.nussknacker.engine.util.ThreadUtils
import pl.touk.nussknacker.engine.util.loader.ScalaServiceLoader
import shapeless.syntax.typeable._

import scala.concurrent.duration._
import scala.language.implicitConversions

/*
  This is main class where we translate Nussknacker model to Flink job.

  NOTE: We should try to use *ONLY* core Flink API here, to avoid version compatibility problems.
  Various NK-dependent Flink hacks should be, if possible, placed in StreamExecutionEnvPreparer.
 */
class FlinkProcessRegistrar(compileProcess: (EspProcess, ProcessVersion) => ClassLoader => CompiledProcessWithDeps,
                            streamExecutionEnvPreparer: StreamExecutionEnvPreparer,
                            eventTimeMetricDuration: FiniteDuration) extends LazyLogging {

  implicit def millisToTime(duration: Long): Time = Time.of(duration, TimeUnit.MILLISECONDS)

  def register(env: StreamExecutionEnvironment, process: EspProcess, processVersion: ProcessVersion, testRunId: Option[TestRunId] = None): Unit = {
    usingRightClassloader(env) {
      val processCompilation = compileProcess(process, processVersion)
      //here we are sure the classloader is ok
      val processWithDeps = processCompilation(UserClassLoader.get("root"))

      streamExecutionEnvPreparer.preRegistration(env, processWithDeps)
      register(env, processCompilation, processWithDeps, testRunId)
      streamExecutionEnvPreparer.postRegistration(env, processWithDeps)
    }
  }

  protected def isRemoteEnv(env: StreamExecutionEnvironment): Boolean = env.getJavaEnv.isInstanceOf[RemoteStreamEnvironment]

  protected def usingRightClassloader(env: StreamExecutionEnvironment)(action: => Unit): Unit = {
    if (!isRemoteEnv(env)) {
      val flinkLoaderSimulation = streamExecutionEnvPreparer.flinkClassLoaderSimulation
      ThreadUtils.withThisAsContextClassLoader[Unit](flinkLoaderSimulation)(action)
    } else {
      action
    }
  }

  protected def prepareWrapAsync(processWithDeps: CompiledProcessWithDeps,
                                 compiledProcessWithDeps: ClassLoader => CompiledProcessWithDeps, globalParameters: Option[NkGlobalParameters])
                                (beforeAsync: DataStream[Context],
                                 node: SplittedNode[_],
                                 validationContext: ValidationContext,
                                 name: String): DataStream[Unit] = {
    val asyncExecutionContextPreparer = processWithDeps.asyncExecutionContextPreparer
    implicit val defaultAsync: DefaultAsyncInterpretationValue = DefaultAsyncInterpretationValueDeterminer.determine(asyncExecutionContextPreparer)
    val metaData = processWithDeps.metaData
    val streamMetaData = MetaDataExtractor.extractTypeSpecificDataOrFail[StreamMetaData](metaData)

    (if (streamMetaData.shouldUseAsyncInterpretation) {
      val asyncFunction = new AsyncInterpretationFunction(compiledProcessWithDeps, node, validationContext, asyncExecutionContextPreparer)
      ExplicitUidInOperatorsSupport.setUidIfNeed(ExplicitUidInOperatorsSupport.defaultExplicitUidInStatefulOperators(globalParameters), node.id + "-$async")(
        new DataStream(org.apache.flink.streaming.api.datastream.AsyncDataStream.orderedWait(beforeAsync.javaStream, asyncFunction,
          processWithDeps.processTimeout.toMillis, TimeUnit.MILLISECONDS, asyncExecutionContextPreparer.bufferSize)))
    } else {
      beforeAsync.flatMap(new SyncInterpretationFunction(compiledProcessWithDeps, node, validationContext))
    }).name(s"${metaData.id}-${node.id}-$name").process(new SplitFunction)
  }

  protected def createInterpreter(compiledProcessWithDepsProvider: ClassLoader => CompiledProcessWithDeps): RuntimeContext => FlinkCompilerLazyInterpreterCreator =
    (runtimeContext: RuntimeContext) =>
      new FlinkCompilerLazyInterpreterCreator(runtimeContext, compiledProcessWithDepsProvider(runtimeContext.getUserCodeClassLoader))

  private def register(env: StreamExecutionEnvironment,
                       compiledProcessWithDeps: ClassLoader => CompiledProcessWithDeps,
                       processWithDeps: CompiledProcessWithDeps,
                       testRunId: Option[TestRunId]): Unit = {

    val metaData = processWithDeps.metaData


    val globalParameters = NkGlobalParameters.readFromContext(env.getConfig)
    def nodeContext(nodeId: String): FlinkCustomNodeContext = {
      FlinkCustomNodeContext(metaData, nodeId, processWithDeps.processTimeout,
        new FlinkLazyParameterFunctionHelper(createInterpreter(compiledProcessWithDeps)),
        processWithDeps.signalSenders, globalParameters)
    }

    val wrapAsync: (DataStream[Context], SplittedNode[_], ValidationContext, String) => DataStream[Unit]
      = prepareWrapAsync(processWithDeps, compiledProcessWithDeps, globalParameters)

    {
      //it is *very* important that source are in correct order here - see ProcessCompiler.compileSources comments
      processWithDeps.sources.toList.foldLeft(Map.empty[BranchEndDefinition, DataStream[InterpretationResult]]) {
        case (branchEnds, next: SourcePart) => branchEnds ++ registerSourcePart(next)
        case (branchEnds, joinPart: CustomNodePart) => branchEnds ++ registerJoinPart(joinPart, branchEnds)
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

      val afterSplit = wrapAsync(newStart, joinPart.node, joinPart.validationContext, "branchInterpretation")
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

      val asyncAssigned = wrapAsync(start, part.node, part.validationContext, "interpretation")

      registerNextParts(asyncAssigned, part)
    }

    //the method returns all possible branch ends in part, together with DataStream leading to them
    def registerNextParts(start: DataStream[Unit], part: PotentiallyStartPart): Map[BranchEndDefinition, DataStream[InterpretationResult]] = {
      val ends = part.ends
      val nextParts = part.nextParts

      start.getSideOutput(OutputTag[InterpretationResult](FlinkProcessRegistrar.EndId))
        .addSink(new EndRateMeterFunction(ends))

      val branchesForParts = nextParts.map { part =>
        registerSubsequentPart(start.getSideOutput(OutputTag[InterpretationResult](part.id)), part)
      }.foldLeft(Map[BranchEndDefinition, DataStream[InterpretationResult]]()) {
        _ ++ _
      }
      val branchForEnds = part.ends.flatMap(_.cast[BranchEnd]).map(be => be.definition ->
        start.getSideOutput(OutputTag[InterpretationResult](be.nodeId))).toMap
      branchesForParts ++ branchForEnds
    }

    def registerSubsequentPart[T](start: DataStream[InterpretationResult],
                                  processPart: SubsequentPart): Map[BranchEndDefinition, DataStream[InterpretationResult]] =
      processPart match {

        case part@SinkPart(sink: FlinkSink, _, validationContext) =>
          val startAfterSinkEvaluated = wrapAsync(start.map(_.finalContext), part.node, validationContext, "function")
            .getSideOutput(OutputTag[InterpretationResult](FlinkProcessRegistrar.EndId))
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

        case part: SinkPart =>
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
          val afterSplit = wrapAsync(newStart, node, validationContext, "customNodeInterpretation")

          registerNextParts(afterSplit, part)
      }
  }
}

object FlinkProcessRegistrar {

  private[registrar] final val EndId = "$end"

  import net.ceedubs.ficus.Ficus._
  import net.ceedubs.ficus.readers.ArbitraryTypeReader._

  // We cannot use LazyLogging trait here because class already has LazyLogging and scala ends with cycle during resolution...
  private lazy val logger: Logger = Logger(LoggerFactory.getLogger(classOf[FlinkProcessRegistrar].getName))

  def apply(compiler: FlinkProcessCompiler, config: Config, prepareExecutionConfig: ExecutionConfigPreparer): FlinkProcessRegistrar = {
    val eventTimeMetricDuration = config.getOrElse[FiniteDuration]("eventTimeMetricSlideDuration", 10.seconds)

    // TODO checkpointInterval is deprecated - remove it in future
    val checkpointInterval = config.getAs[FiniteDuration](path = "checkpointInterval")
    if (checkpointInterval.isDefined) {
      logger.warn("checkpointInterval config property is deprecated, use checkpointConfig.checkpointInterval instead")
    }

    val checkpointConfig = config.getAs[CheckpointConfig](path = "checkpointConfig")
      .orElse(checkpointInterval.map(CheckpointConfig(_)))
    val rocksDBStateBackendConfig = config.getAs[RocksDBStateBackendConfig]("rocksDB").filter(_ => compiler.diskStateBackendSupport)

    val defaultStreamExecutionEnvPreparer =
      ScalaServiceLoader.load[FlinkCompatibilityProvider](getClass.getClassLoader)
        .headOption.map(_.createExecutionEnvPreparer(config, prepareExecutionConfig, compiler.diskStateBackendSupport))
        .getOrElse(new DefaultStreamExecutionEnvPreparer(checkpointConfig, rocksDBStateBackendConfig, prepareExecutionConfig))
    new FlinkProcessRegistrar(compiler.compileProcess, defaultStreamExecutionEnvPreparer, eventTimeMetricDuration)
  }


}

