package pl.touk.nussknacker.engine.process.registrar

import com.typesafe.scalalogging.LazyLogging
import org.apache.flink.api.common.functions.RuntimeContext
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.streaming.api.datastream.{AsyncDataStream, DataStream, SingleOutputStreamOperator}
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import org.apache.flink.util.OutputTag
import pl.touk.nussknacker.engine.InterpretationResult
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.component.NodeComponentInfo
import pl.touk.nussknacker.engine.api.context.{JoinContextTransformation, ValidationContext}
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.compiledgraph.part._
import pl.touk.nussknacker.engine.deployment.DeploymentData
import pl.touk.nussknacker.engine.flink.FlinkScenarioCompilationDependencies
import pl.touk.nussknacker.engine.flink.api.{FlinkEngineContext, NkGlobalParameters, RuntimeCtx}
import pl.touk.nussknacker.engine.flink.api.FlinkEngineContextOps._
import pl.touk.nussknacker.engine.flink.api.process._
import pl.touk.nussknacker.engine.flink.api.typeinformation.TypeInformationDetection
import pl.touk.nussknacker.engine.graph.node.{BranchEndDefinition, NodeData}
import pl.touk.nussknacker.engine.node.NodeComponentInfoExtractor.fromScenarioNode
import pl.touk.nussknacker.engine.process.{ExecutionConfigPreparer, FlinkCompatibilityProvider, FlinkJobConfig}
import pl.touk.nussknacker.engine.process.compiler.{
  FlinkEngineRuntimeContextImpl,
  FlinkProcessCompilerData,
  FlinkProcessCompilerDataFactory,
  UsedNodes
}
import pl.touk.nussknacker.engine.resultcollector.{ProductionServiceInvocationCollector, ResultCollector}
import pl.touk.nussknacker.engine.splittedgraph.{splittednode, SplittedNodesCollector}
import pl.touk.nussknacker.engine.splittedgraph.end.BranchEnd
import pl.touk.nussknacker.engine.testmode.TestServiceInvocationCollector
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap
import pl.touk.nussknacker.engine.util.MetaDataExtractor
import pl.touk.nussknacker.engine.util.loader.ScalaServiceLoader
import shapeless.syntax.typeable.typeableOps

import java.util.concurrent.TimeUnit
import scala.language.implicitConversions

/*
  This is main class where we translate Nussknacker model to Flink job.

  NOTE: We should try to use *ONLY* core Flink API here, to avoid version compatibility problems.
  Various NK-dependent Flink hacks should be, if possible, placed in StreamExecutionEnvPreparer.
 */
class FlinkProcessRegistrar(
    prepareCompilerData: (MetaData, ProcessVersion, ResultCollector) => (
        UsedNodes,
        ClassLoader
    ) => FlinkProcessCompilerData,
    streamExecutionEnvPreparer: StreamExecutionEnvPreparer
) extends LazyLogging {

  import FlinkProcessRegistrar._

  def register(
      env: StreamExecutionEnvironment,
      process: CanonicalProcess,
      processVersion: ProcessVersion,
      deploymentData: DeploymentData
  ): Unit =
    register(env, process, processVersion, deploymentData, ProductionServiceInvocationCollector)

  def register(
      env: StreamExecutionEnvironment,
      process: CanonicalProcess,
      processVersion: ProcessVersion,
      deploymentData: DeploymentData,
      resultCollector: ResultCollector
  ): Unit = {
    // sbt's LayeredClassLoader in most tests, ModelClassLoader on real Flink and on MiniCluster
    val userClassLoader                        = Thread.currentThread().getContextClassLoader
    val compilerDataForUsedNodesAndClassloader = prepareCompilerData(process.metaData, processVersion, resultCollector)
    val compilerData = compilerDataForUsedNodesAndClassloader(UsedNodes.empty, userClassLoader)

    streamExecutionEnvPreparer.preRegistration(env, compilerData, deploymentData)

    val compilerDataForProcessPart =
      FlinkProcessRegistrar.enrichWithUsedNodes[FlinkProcessCompilerData](compilerDataForUsedNodesAndClassloader) _
    register(
      env,
      compilerDataForProcessPart,
      compilerData,
      process,
      resultCollector,
      deploymentData
    )
  }

  private def createInterpreter(
      compilerDataForClassloader: ClassLoader => FlinkProcessCompilerData
  ): RuntimeContext => ToEvaluateFunctionConverterWithLifecycle =
    (runtimeContext: RuntimeContext) =>
      new ToEvaluateFunctionConverterWithLifecycle(
        runtimeContext,
        compilerDataForClassloader(runtimeContext.getUserCodeClassLoader)
      )

  private def register(
      env: StreamExecutionEnvironment,
      compilerDataForProcessPart: Option[ProcessPart] => ClassLoader => FlinkProcessCompilerData,
      compilerData: FlinkProcessCompilerData,
      process: CanonicalProcess,
      resultCollector: ResultCollector,
      deploymentData: DeploymentData
  ): Unit = {
    val globalParameters = NkGlobalParameters.fromMap(env.getConfig.getGlobalJobParameters.toMap)

    def nodeContext(
        nodeComponentId: NodeComponentInfo,
        validationContext: Either[ValidationContext, Map[String, ValidationContext]]
    ): FlinkCustomNodeContext = {
      val exceptionHandlerPreparer = (flinkEngineContext: FlinkEngineContext) =>
        compilerDataForProcessPart(None)(flinkEngineContext.getUserCodeClassLoader).prepareExceptionHandler(
          flinkEngineContext
        )

      val jobData                     = compilerData.jobData
      val componentUseContextProvider = compilerData.runtimeMode

      FlinkCustomNodeContext(
        jobData,
        nodeComponentId.nodeId,
        nodeComponentId.nodeName,
        compilerData.processTimeout,
        convertToEngineRuntimeContext =
          (r: RuntimeContext) => FlinkEngineRuntimeContextImpl(jobData, RuntimeCtx(r), componentUseContextProvider),
        lazyParameterHelper = new FlinkLazyParameterFunctionHelper(
          nodeComponentId,
          exceptionHandlerPreparer.narrowToRuntimeCtx,
          createInterpreter(compilerDataForProcessPart(None))
        ),
        exceptionHandlerPreparer = exceptionHandlerPreparer,
        globalParameters = globalParameters,
        validationContext,
        compilerData.runtimeMode.createContext(
          deploymentData.nodesData.get(nodeComponentId.nodeId)
        ),
        // TODO: we should verify if component supports given node data type. If not, we should throw some error instead
        //       of silently skip these data
      )
    }

    {
      // it is *very* important that source are in correct order here - see ProcessCompiler.compileSources comments
      val compiledScenarioParts = compilerData
        .compileProcessOrFail(process)(new FlinkScenarioCompilationDependencies(env))

      streamExecutionEnvPreparer.postScenarioCompilation(env, compilerData, deploymentData)

      compiledScenarioParts.sources.toList
        .foldLeft(Map.empty[BranchEndDefinition, BranchEndData]) {
          case (branchEnds, next: SourcePart)         => branchEnds ++ registerSourcePart(next)
          case (branchEnds, joinPart: CustomNodePart) => branchEnds ++ registerJoinPart(joinPart, branchEnds)
        }
    }

    def registerSourcePart(part: SourcePart): Map[BranchEndDefinition, BranchEndData] = {
      // TODO: get rid of cast (but how??)
      val source = part.obj.asInstanceOf[FlinkSource]

      val contextTypeInformation = TypeInformationDetection.instance.forContext(part.validationContext)
      val nodeComponentInfo      = nodeComponentInfoFrom(part)

      val start = source
        .contextStream(env, nodeContext(nodeComponentInfo, Left(ValidationContext.empty)))
        .process(
          new SourceMetricsFunction(part.id.value, part.node.data.name.value, compilerData.runtimeMode),
          contextTypeInformation
        )

      val asyncAssigned = registerInterpretationPart(start, part, InterpretationName, nodeComponentInfo)

      registerNextParts(asyncAssigned, part)
    }

    // thanks to correct sorting, we know that branchEnds contain all edges to joinPart
    def registerJoinPart(
        joinPart: CustomNodePart,
        branchEnds: Map[BranchEndDefinition, BranchEndData]
    ): Map[BranchEndDefinition, BranchEndData] = {
      val inputs: Map[String, (DataStream[Context], ValidationContext)] = branchEnds.collect {
        case (BranchEndDefinition(id, joinId), BranchEndData(validationContext, stream))
            if joinPart.id.value == joinId =>
          id -> (
            stream.map(
              (value: InterpretationResult) => value.finalContext,
              TypeInformationDetection.instance.forContext(validationContext)
            ),
            validationContext
          )
      }

      val transformer = joinPart.transformer match {
        case joinTransformer: FlinkCustomJoinTransformation                    => joinTransformer
        case JoinContextTransformation(_, impl: FlinkCustomJoinTransformation) => impl
        case other =>
          throw new IllegalArgumentException(s"Unknown join node transformer: $other")
      }

      val outputVar         = joinPart.node.data.outputVar.get
      val newContextFun     = (ir: ValueWithContext[_]) => ir.context.withVariable(outputVar, ir.value)
      val nodeComponentInfo = nodeComponentInfoFrom(joinPart)
      val newStart = transformer
        .transform(
          inputs.mapValuesNow(_._1),
          nodeContext(nodeComponentInfo, Right(inputs.mapValuesNow(_._2)))
        )
        .map(
          (value: ValueWithContext[AnyRef]) => newContextFun(value),
          TypeInformationDetection.instance.forContext(joinPart.validationContext)
        )

      val afterSplit = registerInterpretationPart(newStart, joinPart, BranchInterpretationName, nodeComponentInfo)
      registerNextParts(afterSplit, joinPart)
    }

    // the method returns all possible branch ends in part, together with DataStream leading to them
    def registerNextParts(
        start: SingleOutputStreamOperator[Unit],
        part: PotentiallyStartPart
    ): Map[BranchEndDefinition, BranchEndData] = {
      val branchesForParts = part.nextParts
        .map { part =>
          val typeInformationForTi =
            InterpretationResultTypeInformation.create(part.contextBefore)
          val typeInformationForVC = TypeInformationDetection.instance.forContext(part.contextBefore)

          registerSubsequentPart(
            sideOutput(start, new OutputTag[InterpretationResult](part.id.value, typeInformationForTi))
              .map((value: InterpretationResult) => value.finalContext, typeInformationForVC),
            part
          )
        }
        .foldLeft(Map[BranchEndDefinition, BranchEndData]()) {
          _ ++ _
        }
      val branchForEnds = part.ends.collect { case TypedEnd(be: BranchEnd, validationContext) =>
        val ti = InterpretationResultTypeInformation.create(validationContext)
        be.definition -> BranchEndData(
          validationContext,
          sideOutput(start, new OutputTag[InterpretationResult](be.nodeId.value, ti))
        )
      }.toMap
      branchesForParts ++ branchForEnds
    }

    def registerSubsequentPart(
        start: SingleOutputStreamOperator[Context],
        processPart: SubsequentPart
    ): Map[BranchEndDefinition, BranchEndData] =
      processPart match {
        case part @ SinkPart(sink: FlinkSink, _, contextBefore, _) =>
          registerSinkPart(start, part, sink, contextBefore)
        case part: SinkPart =>
          // TODO: fixme "part.obj" is not stringified well
          //      (eg. Scenario can only use flink sinks, instead given: pl.touk.nussknacker.engine.management.sample.sink.LiteDeadEndSink$@21220fd7)
          throw new IllegalArgumentException(s"Scenario can only use flink sinks, instead given: ${part.obj}")
        case part: CustomNodePart =>
          registerCustomNodePart(start, part)
      }

    def registerSinkPart(
        start: SingleOutputStreamOperator[Context],
        part: SinkPart,
        sink: FlinkSink,
        contextBefore: ValidationContext
    ): Map[BranchEndDefinition, BranchEndData] = {
      val typeInformationForIR  = InterpretationResultTypeInformation.create(contextBefore)
      val typeInformationForCtx = TypeInformationDetection.instance.forContext(contextBefore)
      val nodeComponentInfo     = nodeComponentInfoFrom(part)
      // TODO: for sinks there are no further nodes to interpret but the function is registered to invoke listeners (e.g. to measure end metrics).
      val afterInterpretation = sideOutput(
        registerInterpretationPart(start, part, SinkInterpretationName, nodeComponentInfo),
        new OutputTag[InterpretationResult](FlinkProcessRegistrar.EndId, typeInformationForIR)
      )
        .map((value: InterpretationResult) => value.finalContext, typeInformationForCtx)
      val customNodeContext = nodeContext(nodeComponentInfo, Left(contextBefore))
      val withValuePrepared = sink.prepareValue(afterInterpretation, customNodeContext)
      // TODO: maybe this logic should be moved to compiler instead?
      val withSinkAdded = resultCollector match {
        case testResultCollector: TestServiceInvocationCollector =>
          val collectingSink = testResultCollector.createSinkInvocationCollector(part.id, part.node.data.name.value)
          val prepareTestValueFun = sink.prepareTestValueFunction
          withValuePrepared
            .map(
              (ds: ValueWithContext[sink.Value]) => ds.map(prepareTestValueFun),
              customNodeContext.valueWithContextInfo.forUnknown
            )
            .sinkTo(
              new CollectingSink[AnyRef](compilerDataForProcessPart(None), collectingSink, part.id, part.node.data.name)
            )
            .uid(part.id.value)
        case _ =>
          sink.registerSink(withValuePrepared, nodeContext(nodeComponentInfo, Left(contextBefore)))
      }

      withSinkAdded.name(operatorName(compilerData.jobData, part.node, "sink"))
      Map()
    }

    def registerCustomNodePart(
        start: DataStream[Context],
        part: CustomNodePart
    ): Map[BranchEndDefinition, BranchEndData] = {
      val transformer = part.transformer match {
        case t: FlinkCustomStreamTransformation => t
        case other =>
          throw new IllegalArgumentException(s"Unknown custom node transformer: $other")
      }
      val nodeComponentInfo = nodeComponentInfoFrom(part)
      val customNodeContext = nodeContext(nodeComponentInfo, Left(part.contextBefore))
      val newContextFun: ValueWithContext[_] => Context = part.node.data.outputVar match {
        case Some(name) => vwc => vwc.context.withVariable(name, vwc.value)
        case None       => _.context
      }
      val transformed = transformer
        .transform(start, customNodeContext)
        .map(
          (value: ValueWithContext[_]) => newContextFun(value),
          TypeInformationDetection.instance.forContext(part.validationContext)
        )
      // TODO: for ending custom nodes there are no further nodes to interpret but the function is registered to invoke listeners (e.g. to measure end metrics).
      val afterInterpretation =
        registerInterpretationPart(transformed, part, CustomNodeInterpretationName, nodeComponentInfo)
      registerNextParts(afterInterpretation, part)
    }

    def registerInterpretationPart(
        stream: SingleOutputStreamOperator[Context],
        part: ProcessPart,
        name: String,
        nodeComponentInfo: NodeComponentInfo
    ): SingleOutputStreamOperator[Unit] = {
      val node              = part.node
      val validationContext = part.validationContext
      val outputContexts = part.ends.map(pe => pe.end.nodeId.value -> pe.validationContext).toMap ++ (part match {
        case e: PotentiallyStartPart => e.nextParts.map(np => np.id.value -> np.validationContext).toMap
        case _                       => Map.empty
      })
      val metaData                      = compilerData.jobData.metaData
      val asyncExecutionContextPreparer = compilerData.asyncExecutionContextPreparer
      val streamMetaData =
        MetaDataExtractor.extractTypeSpecificDataOrDefault[StreamMetaData](metaData, StreamMetaData())

      val configParameters = globalParameters.flatMap(_.configParameters)
      val useIOMonad       = configParameters.flatMap(_.useIOMonadInInterpreter).getOrElse(true)
      val shouldUseAsyncInterpretation =
        AsyncInterpretationDeterminer(configParameters, asyncExecutionContextPreparer).determine(node, streamMetaData)

      val resultStream: DataStream[InterpretationResult] = if (shouldUseAsyncInterpretation) {
        val asyncFunction = new AsyncInterpretationFunction(
          compilerDataForProcessPart(Some(part)),
          node,
          validationContext,
          asyncExecutionContextPreparer,
          nodeComponentInfo,
          useIOMonad
        )
        AsyncDataStream
          .orderedWait(
            stream,
            asyncFunction,
            compilerData.processTimeout.toMillis,
            TimeUnit.MILLISECONDS,
            asyncExecutionContextPreparer.bufferSize
          )
          .uid(node.id.value + "-$async")
      } else {
        val ti = InterpretationResultTypeInformation.create(outputContexts)
        stream.flatMap(
          new SyncInterpretationFunction(
            compilerDataForProcessPart(Some(part)),
            node,
            validationContext,
            nodeComponentInfo,
            useIOMonad
          ),
          ti
        )
      }

      resultStream.getTransformation.setName(
        interpretationOperatorName(metaData, node, name, shouldUseAsyncInterpretation)
      )

      resultStream
        .process(new SplitFunction(outputContexts), TypeInformation.of(classOf[Unit]))
    }

  }

  private def sideOutput[T](stream: SingleOutputStreamOperator[_], tag: OutputTag[T]) =
    streamExecutionEnvPreparer.sideOutputGetter(stream, tag)

  private def nodeComponentInfoFrom(processPart: ProcessPart): NodeComponentInfo = {
    fromScenarioNode(processPart.node.data)
  }

}

object FlinkProcessRegistrar {

  private[registrar] final val EndId     = "$end"
  final val InterpretationName           = "interpretation"
  final val CustomNodeInterpretationName = "customNodeInterpretation"
  final val SinkInterpretationName       = "sinkInterpretation"
  final val BranchInterpretationName     = "branchInterpretation"

  private def enrichWithUsedNodes[T](
      original: (UsedNodes, ClassLoader) => T
  )(part: Option[ProcessPart]): ClassLoader => T = {
    val nodesToUse: Iterable[NodeData] = part.toList
      .flatMap(p => SplittedNodesCollector.collectNodes(p.node).map(_.data))
    val endingParts: Map[NodeId, NodeName] = part
      .flatMap(_.cast[PotentiallyStartPart])
      .toList
      .flatMap(_.nextParts)
      .foldLeft(Map.empty[NodeId, NodeName])((acc, p) => acc + (p.id -> p.node.data.name))
    original(UsedNodes(nodesToUse, endingParts), _)
  }

  def apply(
      compilerFactory: FlinkProcessCompilerDataFactory,
      jobConfig: FlinkJobConfig,
      prepareExecutionConfig: ExecutionConfigPreparer
  ): FlinkProcessRegistrar = {
    val defaultStreamExecutionEnvPreparer =
      ScalaServiceLoader
        .load[FlinkCompatibilityProvider](getClass.getClassLoader)
        .headOption
        .map(_.createExecutionEnvPreparer(jobConfig, prepareExecutionConfig))
        .getOrElse(
          new DefaultStreamExecutionEnvPreparer(jobConfig, prepareExecutionConfig)
        )
    new FlinkProcessRegistrar(compilerFactory.prepareCompilerData, defaultStreamExecutionEnvPreparer)
  }

  private[registrar] def operatorName(
      jobData: JobData,
      splittedNode: splittednode.SplittedNode[NodeData],
      operation: String
  ) = {
    s"${jobData.metaData.name}-${splittedNode.id}-$operation"
  }

  private[registrar] def interpretationOperatorName(
      metaData: MetaData,
      splittedNode: splittednode.SplittedNode[NodeData],
      interpretationName: String,
      shouldUseAsyncInterpretation: Boolean
  ): String = {
    interpretationOperatorName(metaData.name, splittedNode.id.value, interpretationName, shouldUseAsyncInterpretation)
  }

  private[registrar] def interpretationOperatorName(
      scenarioName: ProcessName,
      nodeId: String,
      interpretationName: String,
      shouldUseAsyncInterpretation: Boolean
  ) = {
    s"$scenarioName-$nodeId-$interpretationName${if (shouldUseAsyncInterpretation) "Async" else "Sync"}"
  }

}

case class BranchEndData(validationContext: ValidationContext, stream: DataStream[InterpretationResult])
