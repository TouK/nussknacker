package pl.touk.nussknacker.engine.process.scenariotesting

import org.apache.flink.api.common.restartstrategy.RestartStrategies
import pl.touk.nussknacker.engine.{ModelConfig, ModelData, RuntimeMode}
import pl.touk.nussknacker.engine.ModelData.ExtractDefinitionFun
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.component.{
  ComponentAdditionalConfig,
  ComponentType,
  DesignerWideComponentId,
  NodesDeploymentData
}
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.process.{ProcessConfigCreator, Source}
import pl.touk.nussknacker.engine.api.test.{ScenarioTestData, ScenarioTestJsonRecord, ScenarioTestParametersRecord}
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.compile.nodecompilation.EvaluableLazyParameterCreator
import pl.touk.nussknacker.engine.definition.component.{
  ComponentDefinitionWithImplementation,
  NodeCompilationDependencies
}
import pl.touk.nussknacker.engine.definition.component.ComponentImplementationInvoker.ComponentImplementationSpecificInvocationContext
import pl.touk.nussknacker.engine.definition.fragment.FragmentParametersDefinitionExtractor
import pl.touk.nussknacker.engine.definition.model.ModelDefinition
import pl.touk.nussknacker.engine.flink.api.exception.FlinkEspExceptionConsumer
import pl.touk.nussknacker.engine.flink.api.process.FlinkSourceTestSupport
import pl.touk.nussknacker.engine.flink.util.source.EmptySource
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.graph.node.FragmentInputDefinition
import pl.touk.nussknacker.engine.process.compiler.{ComponentDefinitionContext, FlinkProcessCompilerDataFactory}
import pl.touk.nussknacker.engine.process.exception.FlinkExceptionHandler
import pl.touk.nussknacker.engine.testmode.{ResultsCollectingListener, TestDataPreparer}

import scala.annotation.nowarn

object TestFlinkProcessCompilerDataFactory {

  // TestFlinkProcessCompilerDataFactory have to be serializable, so we pass only the necessary objects
  def apply(
      scenario: CanonicalProcess,
      scenarioTestData: ScenarioTestData,
      modelData: ModelData,
      jobData: JobData,
      collectingListener: ResultsCollectingListener[_]
  ): TestFlinkProcessCompilerDataFactory = {
    new TestFlinkProcessCompilerDataFactory(
      scenario = scenario,
      scenarioTestData = scenarioTestData,
      jobData = jobData,
      creator = modelData.configCreator,
      extractModelDefinition = modelData.extractModelDefinitionFun,
      modelConfig = modelData.modelConfig,
      configsFromProviderWithDictionaryEditor = modelData.additionalConfigsFromProvider,
      collectingListener = collectingListener
    )
  }

}

class TestFlinkProcessCompilerDataFactory(
    scenario: CanonicalProcess,
    scenarioTestData: ScenarioTestData,
    jobData: JobData,
    creator: ProcessConfigCreator,
    extractModelDefinition: ExtractDefinitionFun,
    modelConfig: ModelConfig,
    configsFromProviderWithDictionaryEditor: Map[DesignerWideComponentId, ComponentAdditionalConfig],
    collectingListener: ResultsCollectingListener[_]
) extends FlinkProcessCompilerDataFactory(
      creator = creator,
      extractModelDefinition = extractModelDefinition,
      modelConfig = modelConfig,
      runtimeMode = RuntimeMode.Test,
      configsFromProviderWithDictionaryEditor = configsFromProviderWithDictionaryEditor,
      nodesData = NodesDeploymentData.empty,
      processListeners = List.empty
    ) {

  override protected def adjustListeners(defaults: List[ProcessListener]): List[ProcessListener] =
    collectingListener :: defaults

  override protected def adjustDefinitions(
      originalModelDefinition: ModelDefinition,
      definitionContext: ComponentDefinitionContext,
  ): ModelDefinition = {
    val sourcePreparer = new StubbedSourcePreparer(
      new TestDataPreparer(
        definitionContext.userCodeClassLoader,
        definitionContext.modelDefinitionWithClasses.modelDefinition.expressionConfig,
        definitionContext.dictRegistry,
        definitionContext.modelDefinitionWithClasses.classDefinitions,
        jobData
      ),
      scenarioTestData
    )

    val processedComponents = originalModelDefinition.components.components.map {
      case component if component.componentType == ComponentType.Source =>
        component.withImplementationInvoker(new TestSourceComponentImplementationInvoker(sourcePreparer, component))
      case other => other
    }

    val stubbedSourceForFragments =
      prepareStubbedSourcesForFragmentInputDefinition(definitionContext, sourcePreparer)

    originalModelDefinition
      .copy(components = originalModelDefinition.components.copy(components = processedComponents))
      .withComponents(stubbedSourceForFragments)
  }

  private def prepareStubbedSourcesForFragmentInputDefinition(
      definitionContext: ComponentDefinitionContext,
      sourcePreparer: StubbedSourcePreparer
  ) = {
    val fragmentParametersDefinitionExtractor = new FragmentParametersDefinitionExtractor(
      definitionContext.userCodeClassLoader,
      definitionContext.modelDefinitionWithClasses.classDefinitions,
      modelConfig.globalParametersConfig
    )

    scenario.collectAllNodes.collect { case frag: FragmentInputDefinition =>
      val parameterExpressionsFromTestData: Map[ParameterName, Expression] =
        scenarioTestData.inputRecords
          .find(_.sourceId.id == frag.id)
          .collect { case ScenarioTestParametersRecord(_, parameterExpressions) => parameterExpressions }
          .getOrElse(Map.empty)
      val fragmentSourceDefinitionPreparer = new StubbedFragmentSourceDefinitionPreparer(
        fragmentParametersDefinitionExtractor,
        parameterExpressionsFromTestData,
      )
      // We create source definition only to reuse prepareSourceFactory method.
      // Source will have fragment component type to avoid collisions with normal sources
      val fragmentSourceDef = fragmentSourceDefinitionPreparer.createSourceDefinition(frag.id, frag)
      fragmentSourceDef.withImplementationInvoker(
        new TestSourceComponentImplementationInvoker(sourcePreparer, fragmentSourceDef)
      )
    }
  }

  override protected def exceptionHandler(
      metaData: MetaData,
      modelConfig: ModelConfig,
      listeners: Seq[ProcessListener],
      classLoader: ClassLoader
  ): FlinkExceptionHandler = {
    new TestFlinkExceptionHandler(metaData, modelConfig, listeners, classLoader)
  }

}

class TestSourceComponentImplementationInvoker(
    sourcePreparer: StubbedSourcePreparer,
    sourceFactory: ComponentDefinitionWithImplementation
) extends StubbedComponentImplementationInvoker(sourceFactory) {

  override def invokeOriginalInvoker(
      params: Params,
      compilationDependencies: NodeCompilationDependencies,
      invocationContext: Option[ComponentImplementationSpecificInvocationContext]
  ): Any = {
    // Transform EvaluableLazyParameterCreator's into EvaluableLazyParameter's
    // in order to have them available for resolving when executing tests using form -
    // see e.g. EventGeneratorSourceFactory.parametersToTestData
    val resolvedParams = Params.fromRawValuesMap(params.nameToRawValueMap.map { case (name, value) =>
      name -> resolveParam(value)
    })
    original.invokeMethod(resolvedParams, compilationDependencies, invocationContext)
  }

  override def transformOriginalInvocationResult(
      originalInvocationResult: Any,
      typingResult: TypingResult,
      compilationDependencies: NodeCompilationDependencies
  ): Any = {
    originalInvocationResult match {
      case sourceWithTestSupport: Source with FlinkSourceTestSupport[Object @unchecked] =>
        sourcePreparer.prepareStubbedSource(sourceWithTestSupport, typingResult, compilationDependencies.nodeId)
      case _ =>
        // We allow mixing sources with test support with sources not supporting it - in the second case, they won't generate anything
        EmptySource(typingResult)
    }
  }

  private def resolveParam(param: Any): Any = param match {
    case lazyParameterCreator: EvaluableLazyParameterCreator[_] =>
      sourcePreparer.resolveParam(lazyParameterCreator)
    case other =>
      other
  }

}

class TestFlinkExceptionHandler(
    metaData: MetaData,
    modelConfig: ModelConfig,
    listeners: Seq[ProcessListener],
    classLoader: ClassLoader
) extends FlinkExceptionHandler(metaData, modelConfig, listeners, classLoader) {

  @nowarn("cat=deprecation")
  override def restartStrategy: RestartStrategies.RestartStrategyConfiguration = RestartStrategies.noRestart()

  override val consumer: FlinkEspExceptionConsumer = _ => {}

}
