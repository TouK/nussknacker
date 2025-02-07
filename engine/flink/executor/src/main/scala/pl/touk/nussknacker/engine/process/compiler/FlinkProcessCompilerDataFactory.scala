package pl.touk.nussknacker.engine.process.compiler

import com.typesafe.config.Config
import pl.touk.nussknacker.engine.ModelData.ExtractDefinitionFun
import pl.touk.nussknacker.engine.api.component.{ComponentAdditionalConfig, DesignerWideComponentId}
import pl.touk.nussknacker.engine.api.dict.EngineDictRegistry
import pl.touk.nussknacker.engine.api.namespaces.NamingStrategy
import pl.touk.nussknacker.engine.api.process.{ComponentUseCase, ProcessConfigCreator, ProcessObjectDependencies}
import pl.touk.nussknacker.engine.api.{JobData, MetaData, ProcessListener, ProcessVersion}
import pl.touk.nussknacker.engine.compile._
import pl.touk.nussknacker.engine.compile.nodecompilation.LazyParameterCreationStrategy
import pl.touk.nussknacker.engine.definition.clazz.{ClassDefinition, ClassDefinitionSet}
import pl.touk.nussknacker.engine.definition.globalvariables.ExpressionConfigDefinition
import pl.touk.nussknacker.engine.definition.model.{ModelDefinition, ModelDefinitionWithClasses}
import pl.touk.nussknacker.engine.dict.DictServicesFactoryLoader
import pl.touk.nussknacker.engine.graph.node
import pl.touk.nussknacker.engine.graph.node.{CustomNode, NodeData}
import pl.touk.nussknacker.engine.process.async.DefaultServiceExecutionContextPreparer
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
    namingStrategy: NamingStrategy,
    componentUseCase: ComponentUseCase,
    configsFromProviderWithDictionaryEditor: Map[DesignerWideComponentId, ComponentAdditionalConfig]
) extends Serializable {

  import net.ceedubs.ficus.Ficus._
  import net.ceedubs.ficus.readers.ArbitraryTypeReader._

  def this(modelData: ModelData) = this(
    modelData.configCreator,
    modelData.extractModelDefinitionFun,
    modelData.modelConfig,
    modelData.namingStrategy,
    componentUseCase = ComponentUseCase.EngineRuntime,
    modelData.additionalConfigsFromProvider
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
    val modelDependencies = ProcessObjectDependencies.withConfig(modelConfig)

    // TODO: this should be somewhere else?
    val timeout = modelConfig.as[FiniteDuration]("timeout")

    // TODO: should this be the default?
    val asyncExecutionContextPreparer = creator
      .asyncExecutionContextPreparer(modelDependencies)
      .getOrElse(
        modelConfig.as[DefaultServiceExecutionContextPreparer]("asyncExecutionConfig")
      )
    val defaultListeners = prepareDefaultListeners(usedNodes) ++ creator.listeners(modelDependencies)
    val listenersToUse   = adjustListeners(defaultListeners, modelDependencies)

    val (definitionWithTypes, dictRegistry) = definitions(modelDependencies, userCodeClassLoader)

    val customProcessValidator = CustomProcessValidatorLoader.loadProcessValidators(userCodeClassLoader, modelConfig)
    val compilerData =
      ProcessCompilerData.prepare(
        JobData(metaData, processVersion),
        definitionWithTypes,
        dictRegistry,
        listenersToUse,
        userCodeClassLoader,
        resultCollector,
        componentUseCase,
        customProcessValidator,
        nonServicesLazyParamStrategy = LazyParameterCreationStrategy.postponed
      )

    new FlinkProcessCompilerData(
      compilerData = compilerData,
      exceptionHandler = exceptionHandler(metaData, modelDependencies, listenersToUse, userCodeClassLoader),
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

  private def definitions(
      modelDependencies: ProcessObjectDependencies,
      userCodeClassLoader: ClassLoader
  ): (ModelDefinitionWithClasses, EngineDictRegistry) = {
    val dictRegistryFactory = loadDictRegistry(userCodeClassLoader)
    val modelDefinitionWithTypes = ModelDefinitionWithClasses(
      extractModelDefinition(
        userCodeClassLoader,
        modelDependencies,
        id => DesignerWideComponentId(id.toString),
        configsFromProviderWithDictionaryEditor
      )
    )
    val dictRegistry = dictRegistryFactory.createEngineDictRegistry(
      modelDefinitionWithTypes.modelDefinition.expressionConfig.dictionaries
    )
    val definitionContext = ComponentDefinitionContext(
      userCodeClassLoader,
      dictRegistry,
      modelDefinitionWithTypes.modelDefinition.expressionConfig,
      modelDefinitionWithTypes.classDefinitions
    )
    val adjustedDefinitions = adjustDefinitions(
      modelDefinitionWithTypes.modelDefinition,
      definitionContext,
      modelDefinitionWithTypes.classDefinitions
    )
    (ModelDefinitionWithClasses(adjustedDefinitions), dictRegistry)
  }

  protected def adjustDefinitions(
      originalModelDefinition: ModelDefinition,
      definitionContext: ComponentDefinitionContext,
      classDefinitions: ClassDefinitionSet,
  ): ModelDefinition = originalModelDefinition

  private def loadDictRegistry(userCodeClassLoader: ClassLoader) = {
    // we are loading DictServicesFactory on TaskManager side. It may be tricky because of class loaders...
    DictServicesFactoryLoader.justOne(userCodeClassLoader)
  }

  protected def adjustListeners(
      defaults: List[ProcessListener],
      modelDependencies: ProcessObjectDependencies
  ): List[ProcessListener] = defaults

  protected def exceptionHandler(
      metaData: MetaData,
      modelDependencies: ProcessObjectDependencies,
      listeners: Seq[ProcessListener],
      classLoader: ClassLoader
  ): FlinkExceptionHandler = {
    new FlinkExceptionHandler(metaData, modelDependencies, listeners, classLoader)
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

case class ComponentDefinitionContext(
    userCodeClassLoader: ClassLoader,
    // below are for purpose of TestDataPreparer
    dictRegistry: EngineDictRegistry,
    expressionConfig: ExpressionConfigDefinition,
    classDefinitions: ClassDefinitionSet
)
