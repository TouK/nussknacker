package pl.touk.nussknacker.engine.process.registrar

import java.util.concurrent.TimeUnit

import com.typesafe.scalalogging.{LazyLogging, Logger}
import org.apache.flink.api.common.functions.RuntimeContext
import org.apache.flink.streaming.api.environment.RemoteStreamEnvironment
import org.apache.flink.streaming.api.scala.{DataStream, OutputTag, StreamExecutionEnvironment}
import org.apache.flink.streaming.api.windowing.time.Time
import org.slf4j.LoggerFactory
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.async.{DefaultAsyncInterpretationValue, DefaultAsyncInterpretationValueDeterminer}
import pl.touk.nussknacker.engine.api.context.{ContextTransformation, JoinContextTransformation, ValidationContext}
import pl.touk.nussknacker.engine.api.deployment.DeploymentData
import pl.touk.nussknacker.engine.testmode.{SinkInvocationCollector, TestRunId, TestServiceInvocationCollector}
import pl.touk.nussknacker.engine.api.typed.typing.Unknown
import pl.touk.nussknacker.engine.flink.api.typeinformation.TypeInformationDetection
import pl.touk.nussknacker.engine.compiledgraph.part._
import pl.touk.nussknacker.engine.flink.api.NkGlobalParameters
import pl.touk.nussknacker.engine.flink.api.compat.ExplicitUidInOperatorsSupport
import pl.touk.nussknacker.engine.flink.api.process.{FlinkCustomJoinTransformation, _}
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.graph.node.BranchEndDefinition
import pl.touk.nussknacker.engine.process.compiler.{FlinkProcessCompiler, FlinkProcessCompilerData}
import pl.touk.nussknacker.engine.process.typeinformation.TypeInformationDetectionUtils
import pl.touk.nussknacker.engine.process.util.StateConfiguration.RocksDBStateBackendConfig
import pl.touk.nussknacker.engine.process.util.UserClassLoader
import pl.touk.nussknacker.engine.process.{CheckpointConfig, ExecutionConfigPreparer, FlinkCompatibilityProvider}
import pl.touk.nussknacker.engine.resultcollector.{ProductionServiceInvocationCollector, ResultCollector}
import pl.touk.nussknacker.engine.splittedgraph.end.BranchEnd
import pl.touk.nussknacker.engine.util.{MetaDataExtractor, ThreadUtils}
import pl.touk.nussknacker.engine.util.loader.ScalaServiceLoader

import scala.concurrent.duration._
import scala.language.implicitConversions

/*
  This is main class where we translate Nussknacker model to Flink job.

  NOTE: We should try to use *ONLY* core Flink API here, to avoid version compatibility problems.
  Various NK-dependent Flink hacks should be, if possible, placed in StreamExecutionEnvPreparer.
 */
class FlinkProcessRegistrar(compileProcess: (EspProcess, ProcessVersion, DeploymentData, ResultCollector) => ClassLoader => FlinkProcessCompilerData,
                            streamExecutionEnvPreparer: StreamExecutionEnvPreparer,
                            eventTimeMetricDuration: FiniteDuration) extends LazyLogging {

  implicit def millisToTime(duration: Long): Time = Time.of(duration, TimeUnit.MILLISECONDS)

  def register(env: StreamExecutionEnvironment, process: EspProcess, processVersion: ProcessVersion, deploymentData: DeploymentData, testRunId: Option[TestRunId] = None): Unit = {
    usingRightClassloader(env) {
      //TODO: move creation outside Registrar, together with refactoring SinkInvocationCollector...
      val collector = testRunId.map(new TestServiceInvocationCollector(_)).getOrElse(ProductionServiceInvocationCollector)

      val processCompilation = compileProcess(process, processVersion, deploymentData, collector)
      val userClassLoader = UserClassLoader.get("root")
      //here we are sure the classloader is ok
      val processWithDeps = processCompilation(userClassLoader)

      streamExecutionEnvPreparer.preRegistration(env, processWithDeps)
      val typeInformationDetection = TypeInformationDetectionUtils.forExecutionConfig(env.getConfig, userClassLoader)
      register(env, processCompilation, processWithDeps, testRunId, typeInformationDetection)
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

  protected def prepareWrapAsync(processWithDeps: FlinkProcessCompilerData,
                                 compiledProcessWithDeps: ClassLoader => FlinkProcessCompilerData,
                                 globalParameters: Option[NkGlobalParameters], typeInformationDetection: TypeInformationDetection)
                                (beforeAsync: DataStream[Context],
                                 part: ProcessPart,
                                 name: String): DataStream[Unit] = {
    val node = part.node
    val validationContext = part.validationContext
    val outputContexts = part.ends.map(pe => pe.end.nodeId -> pe.validationContext).toMap ++ (part match {
      case e:PotentiallyStartPart => e.nextParts.map(np => np.id -> np.validationContext).toMap
      case _ => Map.empty
    })
    val asyncExecutionContextPreparer = processWithDeps.asyncExecutionContextPreparer
    implicit val defaultAsync: DefaultAsyncInterpretationValue = DefaultAsyncInterpretationValueDeterminer.determine(asyncExecutionContextPreparer)
    val metaData = processWithDeps.metaData
    val streamMetaData = MetaDataExtractor.extractTypeSpecificDataOrFail[StreamMetaData](metaData)

    val useIOMonad = globalParameters.flatMap(_.configParameters).flatMap(_.useIOMonadInInterpreter).getOrElse(true)
    //TODO: we should detect automatically that Interpretation has no async enrichers and invoke sync function then, as async comes with
    //performance penalty...
    (if (streamMetaData.shouldUseAsyncInterpretation) {
      val asyncFunction = new AsyncInterpretationFunction(compiledProcessWithDeps, node, validationContext, asyncExecutionContextPreparer, useIOMonad)
      ExplicitUidInOperatorsSupport.setUidIfNeed(ExplicitUidInOperatorsSupport.defaultExplicitUidInStatefulOperators(globalParameters), node.id + "-$async")(
        new DataStream(org.apache.flink.streaming.api.datastream.AsyncDataStream.orderedWait(beforeAsync.javaStream, asyncFunction,
          processWithDeps.processTimeout.toMillis, TimeUnit.MILLISECONDS, asyncExecutionContextPreparer.bufferSize)))
    } else {
      val ti = typeInformationDetection.forInterpretationResults(outputContexts)
      beforeAsync.flatMap(new SyncInterpretationFunction(compiledProcessWithDeps, node, validationContext, useIOMonad))(ti)
    }).name(s"${metaData.id}-${node.id}-$name")
      .process(new SplitFunction(outputContexts, typeInformationDetection))(org.apache.flink.streaming.api.scala.createTypeInformation[Unit])
  }

  protected def createInterpreter(compiledProcessWithDepsProvider: ClassLoader => FlinkProcessCompilerData): RuntimeContext => FlinkCompilerLazyInterpreterCreator =
    (runtimeContext: RuntimeContext) =>
      new FlinkCompilerLazyInterpreterCreator(runtimeContext, compiledProcessWithDepsProvider(runtimeContext.getUserCodeClassLoader))

  private def register(env: StreamExecutionEnvironment,
                       compiledProcessWithDeps: ClassLoader => FlinkProcessCompilerData,
                       processWithDeps: FlinkProcessCompilerData,
                       testRunId: Option[TestRunId], typeInformationDetection: TypeInformationDetection): Unit = {

    val metaData = processWithDeps.metaData
    val globalParameters = NkGlobalParameters.readFromContext(env.getConfig)
    def nodeContext(nodeId: String, validationContext: Either[ValidationContext, Map[String, ValidationContext]]): FlinkCustomNodeContext = {
      FlinkCustomNodeContext(processWithDeps.jobData, nodeId, processWithDeps.processTimeout,
        lazyParameterHelper = new FlinkLazyParameterFunctionHelper(createInterpreter(compiledProcessWithDeps)),
        signalSenderProvider = processWithDeps.signalSenders,
        exceptionHandlerPreparer = runtimeContext => compiledProcessWithDeps(runtimeContext.getUserCodeClassLoader).prepareExceptionHandler(runtimeContext),
        globalParameters = globalParameters,
        validationContext,
        typeInformationDetection,
        processWithDeps.runMode)
    }

    val wrapAsync: (DataStream[Context], ProcessPart, String) => DataStream[Unit]
      = prepareWrapAsync(processWithDeps, compiledProcessWithDeps, globalParameters, typeInformationDetection)

    {
      //it is *very* important that source are in correct order here - see ProcessCompiler.compileSources comments
      processWithDeps.compileProcess().sources.toList.foldLeft(Map.empty[BranchEndDefinition, BranchEndData]) {
        case (branchEnds, next: SourcePart) => branchEnds ++ registerSourcePart(next)
        case (branchEnds, joinPart: CustomNodePart) => branchEnds ++ registerJoinPart(joinPart, branchEnds)
      }
    }

    //thanks to correct sorting, we know that branchEnds contain all edges to joinPart
    def registerJoinPart(joinPart: CustomNodePart,
                         branchEnds: Map[BranchEndDefinition, BranchEndData]): Map[BranchEndDefinition, BranchEndData] = {
      val inputs: Map[String, (DataStream[Context], ValidationContext)] = branchEnds.collect {
        case (BranchEndDefinition(id, joinId), BranchEndData(validationContext, stream)) if joinPart.id == joinId =>
          id -> (stream.map(_.finalContext)(typeInformationDetection.forContext(validationContext)), validationContext)
      }


      val transformer = joinPart.transformer match {
        case joinTransformer: FlinkCustomJoinTransformation => joinTransformer
        case JoinContextTransformation(_, impl: FlinkCustomJoinTransformation) => impl
        case other =>
          throw new IllegalArgumentException(s"Unknown join node transformer: $other")
      }

      val outputVar = joinPart.node.data.outputVar.get
      val newContextFun = (ir: ValueWithContext[_]) => ir.context.withVariable(outputVar, ir.value)

      val newStart = transformer
        .transform(inputs.mapValues(_._1), nodeContext(joinPart.id, Right(inputs.mapValues(_._2))))
        .map(newContextFun)(typeInformationDetection.forContext(joinPart.validationContext))

      val afterSplit = wrapAsync(newStart, joinPart, "branchInterpretation")
      registerNextParts(afterSplit, joinPart)
    }

    def registerSourcePart(part: SourcePart): Map[BranchEndDefinition, BranchEndData] = {
      //TODO: get rid of cast (but how??)
      val source = part.obj.asInstanceOf[FlinkSource[Any]]

      val contextTypeInformation = typeInformationDetection.forContext(part.validationContext)

      val start = source
        .sourceStream(env, nodeContext(part.id, Left(ValidationContext.empty)))
        .process(new EventTimeDelayMeterFunction[Context]("eventtimedelay", part.id, eventTimeMetricDuration))(contextTypeInformation)
        .map(new RateMeterFunction[Context]("source", part.id))(contextTypeInformation)

      val asyncAssigned = wrapAsync(start, part, "interpretation")

      registerNextParts(asyncAssigned, part)
    }

    //the method returns all possible branch ends in part, together with DataStream leading to them
    def registerNextParts(start: DataStream[Unit], part: PotentiallyStartPart): Map[BranchEndDefinition, BranchEndData] = {
      val ends = part.ends
      val nextParts = part.nextParts

      //FIXME: is this correct value?
      val typeInformationForEnd = typeInformationDetection.forInterpretationResult(ValidationContext.empty, None)
      start.getSideOutput(OutputTag[InterpretationResult](FlinkProcessRegistrar.EndId)(typeInformationForEnd))(typeInformationForEnd)
        .addSink(new EndRateMeterFunction(ends))

      val branchesForParts = nextParts.map { part =>
        val typeInformationForTi = typeInformationDetection.forInterpretationResult(part.contextBefore, None)
        val typeInformationForVC = typeInformationDetection.forContext(part.contextBefore)

        registerSubsequentPart(start.getSideOutput(OutputTag[InterpretationResult](part.id)(typeInformationForTi))(typeInformationForTi)
          .map(_.finalContext)(typeInformationForVC), part)
      }.foldLeft(Map[BranchEndDefinition, BranchEndData]()) {
        _ ++ _
      }
      val branchForEnds = part.ends.collect {
        case TypedEnd(be:BranchEnd, validationContext) =>
          val ti = typeInformationDetection.forInterpretationResult(validationContext, None)
          be.definition -> BranchEndData(validationContext, start.getSideOutput(OutputTag[InterpretationResult](be.nodeId)(ti))(ti))
      }.toMap
      branchesForParts ++ branchForEnds
    }

    def registerSubsequentPart[T](start: DataStream[Context],
                                  processPart: SubsequentPart): Map[BranchEndDefinition, BranchEndData] =
      processPart match {

        case part@SinkPart(sink: FlinkSink, _, contextBefore, _) =>

          //TODO: type expression??
          val typeInformation = typeInformationDetection.forInterpretationResult(contextBefore, Some(Unknown))

          val startAfterSinkEvaluated = wrapAsync(start, part, "function")
            .getSideOutput(OutputTag[InterpretationResult](FlinkProcessRegistrar.EndId)(typeInformation))(typeInformation)
            .map(new EndRateMeterFunction(part.ends))(typeInformation)

          //TODO: maybe this logic should be moved to compiler instead?
          val withSinkAdded = testRunId match {
            case None =>
              sink.registerSink(startAfterSinkEvaluated, nodeContext(part.id, Left(contextBefore)))
            case Some(runId) =>
              val typ = part.node.data.ref.typ
              val prepareFunction = sink.testDataOutput.getOrElse(throw new IllegalArgumentException(s"Sink $typ cannot be mocked"))
              val collectingSink = SinkInvocationCollector(runId, part.id, typ, prepareFunction)
              startAfterSinkEvaluated.addSink(new CollectingSinkFunction(compiledProcessWithDeps, collectingSink, part.id))
          }

          withSinkAdded.name(s"${metaData.id}-${part.id}-sink")
          Map()

        case part: SinkPart =>
          throw new IllegalArgumentException(s"Process can only use flink sinks, instead given: ${part.obj}")
        case part@CustomNodePart(transformerObj, node, contextBefore, contextAfter, _, _) =>

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

          val customNodeContext = nodeContext(part.id, Left(part.contextBefore))
          val newStart = transformer
            .transform(start, customNodeContext)
            .map(newContextFun)(typeInformationDetection.forContext(contextAfter))
          val afterSplit = wrapAsync(newStart, part, "customNodeInterpretation")

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

  def apply(compiler: FlinkProcessCompiler, prepareExecutionConfig: ExecutionConfigPreparer): FlinkProcessRegistrar = {
    val config = compiler.processConfig
    val eventTimeMetricDuration = config.getOrElse[FiniteDuration]("eventTimeMetricSlideDuration", 10.seconds)

    val checkpointConfig = config.getAs[CheckpointConfig](path = "checkpointConfig")
    val rocksDBStateBackendConfig = config.getAs[RocksDBStateBackendConfig]("rocksDB").filter(_ => compiler.diskStateBackendSupport)

    val defaultStreamExecutionEnvPreparer =
      ScalaServiceLoader.load[FlinkCompatibilityProvider](getClass.getClassLoader)
        .headOption.map(_.createExecutionEnvPreparer(config, prepareExecutionConfig, compiler.diskStateBackendSupport))
        .getOrElse(new DefaultStreamExecutionEnvPreparer(checkpointConfig, rocksDBStateBackendConfig, prepareExecutionConfig))
    new FlinkProcessRegistrar(compiler.compileProcess, defaultStreamExecutionEnvPreparer, eventTimeMetricDuration)
  }


}

case class BranchEndData(validationContext: ValidationContext, stream: DataStream[InterpretationResult])


