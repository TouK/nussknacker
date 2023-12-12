package pl.touk.nussknacker.engine.process.compiler

import com.typesafe.config.Config
import pl.touk.nussknacker.engine.ModelData.ExtractDefinitionFun
import pl.touk.nussknacker.engine.api.dict.EngineDictRegistry
import pl.touk.nussknacker.engine.api.namespaces.ObjectNaming
import pl.touk.nussknacker.engine.api.process.{ComponentUseCase, ProcessConfigCreator, ProcessObjectDependencies}
import pl.touk.nussknacker.engine.api.{JobData, MetaData, ProcessListener, ProcessVersion}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.compile._
import pl.touk.nussknacker.engine.definition.model.ModelDefinitionWithClasses
import pl.touk.nussknacker.engine.dict.DictServicesFactoryLoader
import pl.touk.nussknacker.engine.graph.node
import pl.touk.nussknacker.engine.graph.node.{CustomNode, NodeData}
import pl.touk.nussknacker.engine.process.async.DefaultAsyncExecutionConfigPreparer
import pl.touk.nussknacker.engine.process.exception.FlinkExceptionHandler
import pl.touk.nussknacker.engine.resultcollector.ResultCollector
import pl.touk.nussknacker.engine.util.LoggingListener
import pl.touk.nussknacker.engine.util.metrics.common.{EndCountingListener, NodeCountingListener}
import pl.touk.nussknacker.engine.{CustomProcessValidatorLoader, ModelData}

import scala.concurrent.duration.FiniteDuration

/*
  This class prepares (in compile method) various objects needed to run process part on one Flink operator.

  Instances of this class is serialized in Flink Job graph, on jobmanager etc. That's why we struggle to keep parameters as small as possible
  and we have InputConfigDuringExecution with ModelConfigLoader and not whole config.
 */
class FlinkProcessCompilerDataFactory(
    creator: ProcessConfigCreator,
    extractModelDefinition: ExtractDefinitionFun,
    modelConfig: Config,
    objectNaming: ObjectNaming,
    componentUseCase: ComponentUseCase,
) extends Serializable {

  import net.ceedubs.ficus.Ficus._
  import net.ceedubs.ficus.readers.ArbitraryTypeReader._

  def this(modelData: ModelData) = this(
    modelData.configCreator,
    modelData.extractModelDefinitionFun,
    modelData.modelConfig,
    modelData.objectNaming,
    componentUseCase = ComponentUseCase.EngineRuntime,
  )

  def prepareCompilerData(
      metaData: MetaData,
      processVersion: ProcessVersion,
      resultCollector: ResultCollector,
      userCodeClassLoader: ClassLoader
  ): FlinkProcessCompilerData =
    prepareCompilerData(metaData, processVersion, resultCollector)(UsedNodes.empty, userCodeClassLoader)

  def prepareCompilerData(metaData: MetaData, processVersion: ProcessVersion, resultCollector: ResultCollector)(
      usedNodes: UsedNodes,
      userCodeClassLoader: ClassLoader
  ): FlinkProcessCompilerData = {
    val processObjectDependencies = ProcessObjectDependencies(modelConfig, objectNaming)

    // TODO: this should be somewhere else?
    val timeout = modelConfig.as[FiniteDuration]("timeout")

    // TODO: should this be the default?
    val asyncExecutionContextPreparer = creator
      .asyncExecutionContextPreparer(processObjectDependencies)
      .getOrElse(
        modelConfig.as[DefaultAsyncExecutionConfigPreparer]("asyncExecutionConfig")
      )
    val defaultListeners = prepareDefaultListeners(usedNodes) ++ creator.listeners(processObjectDependencies)
    val listenersToUse   = adjustListeners(defaultListeners, processObjectDependencies)

    val (definitionWithTypes, dictRegistry) = definitions(processObjectDependencies, userCodeClassLoader)

    val customProcessValidator = CustomProcessValidatorLoader.loadProcessValidators(userCodeClassLoader, modelConfig)
    val compilerData =
      ProcessCompilerData.prepare(
        modelConfig,
        definitionWithTypes,
        dictRegistry,
        listenersToUse,
        userCodeClassLoader,
        resultCollector,
        componentUseCase,
        customProcessValidator
      )

    new FlinkProcessCompilerData(
      compilerData = compilerData,
      jobData = JobData(metaData, processVersion),
      exceptionHandler = exceptionHandler(metaData, processObjectDependencies, listenersToUse, userCodeClassLoader),
      asyncExecutionContextPreparer = asyncExecutionContextPreparer,
      processTimeout = timeout,
      componentUseCase = componentUseCase
    )
  }

  // TODO: should this be configurable somehow?
  private def prepareDefaultListeners(usedNodes: UsedNodes) = {
    // see comment in Interpreter.interpretNext
    // TODO: how should we handle branch?
    val nodesHandledInNextParts = (nodeData: NodeData) =>
      nodeData.isInstanceOf[CustomNode] || nodeData.isInstanceOf[node.Sink]
    List(
      LoggingListener,
      new NodeCountingListener(usedNodes.nodes.filterNot(nodesHandledInNextParts).map(_.id) ++ usedNodes.nextParts),
      new EndCountingListener(usedNodes.nodes)
    )
  }

  // TODO: We already passed extractModelDefinition function to compiler - we shouldn't transform this definition again.
  //       It should be merged
  protected def definitions(
      processObjectDependencies: ProcessObjectDependencies,
      userCodeClassLoader: ClassLoader
  ): (ModelDefinitionWithClasses, EngineDictRegistry) = {
    val dictRegistryFactory = loadDictRegistry(userCodeClassLoader)
    val modelDefinitionWithTypes = ModelDefinitionWithClasses(
      extractModelDefinition(
        userCodeClassLoader,
        processObjectDependencies,
      )
    )
    val dictRegistry = dictRegistryFactory.createEngineDictRegistry(
      modelDefinitionWithTypes.modelDefinition.expressionConfig.dictionaries
    )
    (modelDefinitionWithTypes, dictRegistry)
  }

  private def loadDictRegistry(userCodeClassLoader: ClassLoader) = {
    // we are loading DictServicesFactory on TaskManager side. It may be tricky because of class loaders...
    DictServicesFactoryLoader.justOne(userCodeClassLoader)
  }

  protected def adjustListeners(
      defaults: List[ProcessListener],
      processObjectDependencies: ProcessObjectDependencies
  ): List[ProcessListener] = defaults

  protected def exceptionHandler(
      metaData: MetaData,
      processObjectDependencies: ProcessObjectDependencies,
      listeners: Seq[ProcessListener],
      classLoader: ClassLoader
  ): FlinkExceptionHandler = {
    new FlinkExceptionHandler(metaData, processObjectDependencies, listeners, classLoader)
  }

}

//The logic of listeners invocation is a bit tricky for CustomNodes/Sinks (see Interpreter.interpretNode)
//ProcessListener.nodeEntered method is invoked in Interpreter, when PartRef is encountered. This is because CustomNode/Sink
//in Interpreter is invoked *after* node execution in Flink/Lite. To initialize Metric listeners correctly we need
//to pass list of nextParts, so when Interpreter invokes nodeEntered on PartRef, the counter is properly initialized
private[process] case class UsedNodes(nodes: Iterable[NodeData], nextParts: Iterable[String])

object UsedNodes {
  val empty: UsedNodes = UsedNodes(Nil, Nil)
}
