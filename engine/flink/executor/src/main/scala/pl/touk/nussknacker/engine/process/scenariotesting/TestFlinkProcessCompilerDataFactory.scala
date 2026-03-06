package pl.touk.nussknacker.engine.process.scenariotesting

import com.typesafe.scalalogging.LazyLogging
import org.apache.flink.configuration.{Configuration, RestartStrategyOptions}
import org.apache.flink.configuration.RestartStrategyOptions.RestartStrategyType
import pl.touk.nussknacker.engine.{ModelConfig, ModelData, RuntimeMode}
import pl.touk.nussknacker.engine.ModelData.ExtractDefinitionFun
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.component.{
  ComponentAdditionalConfig,
  ComponentType,
  DesignerWideComponentId,
  NodesDeploymentData
}
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.process.{ProcessConfigCreator, Source}
import pl.touk.nussknacker.engine.api.test.ScenarioTestData
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.compile.nodecompilation.{
  EvaluableLazyParameterCreator,
  StaticComponentOutputValidationContextDeterminer
}
import pl.touk.nussknacker.engine.definition.component.{
  ComponentDefinitionWithImplementation,
  NodeCompilationDependencies
}
import pl.touk.nussknacker.engine.definition.component.ComponentImplementationInvoker.ComponentImplementationSpecificInvocationContext
import pl.touk.nussknacker.engine.definition.fragment.FragmentParametersDefinitionExtractor
import pl.touk.nussknacker.engine.definition.model.ModelDefinition
import pl.touk.nussknacker.engine.flink.api.exception.FlinkEspExceptionConsumer
import pl.touk.nussknacker.engine.flink.api.process.FlinkSourceTestSupport
import pl.touk.nussknacker.engine.graph.node.FragmentInputDefinition
import pl.touk.nussknacker.engine.process.compiler.{ComponentDefinitionContext, FlinkProcessCompilerDataFactory}
import pl.touk.nussknacker.engine.process.exception.FlinkExceptionHandler
import pl.touk.nussknacker.engine.testmode.{ResultsCollectingListener, TestDataPreparer}
import pl.touk.nussknacker.engine.util.watermarkstrategy.WithWatermarkStrategyOptions
import pl.touk.nussknacker.engine.variables.GlobalVariablesPreparer
import shapeless.syntax.typeable.typeableOps

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
    )
    with LazyLogging {

  override protected def adjustListeners(defaults: List[ProcessListener]): List[ProcessListener] =
    collectingListener :: defaults

  override protected def adjustDefinitions(
      originalModelDefinition: ModelDefinition,
      definitionContext: ComponentDefinitionContext,
  ): ModelDefinition = {
    val sourceSpecificFormatStubbedSourcePreparer = new SourceSpecificFormatStubbedSourcePreparer(
      new TestDataPreparer(
        definitionContext.userCodeClassLoader,
        definitionContext.modelDefinitionWithClasses.modelDefinition.expressionConfig,
        definitionContext.dictRegistry,
        definitionContext.modelDefinitionWithClasses.classDefinitions,
        jobData
      ),
      scenarioTestData
    )

    val outputValidationContextDeterminer = new StaticComponentOutputValidationContextDeterminer(
      GlobalVariablesPreparer(definitionContext.modelDefinitionWithClasses.modelDefinition.expressionConfig)
    )

    val processedComponents = originalModelDefinition.components.components.map {
      case component if component.componentType == ComponentType.Source =>
        component.withImplementationInvoker(
          new TestSourceComponentImplementationInvoker(
            sourceSpecificFormatStubbedSourcePreparer,
            outputValidationContextDeterminer,
            component
          )
        )
      case other => other
    }

    val stubbedSourceForFragments =
      prepareStubbedSourcesForFragmentInputDefinition(
        definitionContext,
        sourceSpecificFormatStubbedSourcePreparer,
        outputValidationContextDeterminer
      )

    originalModelDefinition
      .copy(components = originalModelDefinition.components.copy(components = processedComponents))
      .withComponents(stubbedSourceForFragments)
  }

  private def prepareStubbedSourcesForFragmentInputDefinition(
      definitionContext: ComponentDefinitionContext,
      sourceSpecificFormatStubbedSourcePreparer: SourceSpecificFormatStubbedSourcePreparer,
      outputValidationContextDeterminer: StaticComponentOutputValidationContextDeterminer
  ) = {
    val fragmentParametersDefinitionExtractor = new FragmentParametersDefinitionExtractor(
      definitionContext.userCodeClassLoader,
      definitionContext.modelDefinitionWithClasses.classDefinitions,
      modelConfig.globalParametersConfig
    )
    val fragmentSourceDefinitionPreparer = new StubbedFragmentSourceDefinitionPreparer(
      fragmentParametersDefinitionExtractor
    )

    scenario.collectAllNodes.collect { case frag: FragmentInputDefinition =>
      // We create source definition only to reuse prepareSourceFactory method.
      // Source will have fragment component type to avoid collisions with normal sources
      val fragmentSourceDef = fragmentSourceDefinitionPreparer.createSourceDefinition(frag.id, frag)
      fragmentSourceDef.withImplementationInvoker(
        new TestSourceComponentImplementationInvoker(
          sourceSpecificFormatStubbedSourcePreparer,
          outputValidationContextDeterminer,
          fragmentSourceDef
        )
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

  private class TestSourceComponentImplementationInvoker(
      sourceSpecificFormatStubbedSourcePreparer: SourceSpecificFormatStubbedSourcePreparer,
      outputValidationContextDeterminer: StaticComponentOutputValidationContextDeterminer,
      sourceFactory: ComponentDefinitionWithImplementation
  ) extends StubbedComponentImplementationInvoker(sourceFactory, outputValidationContextDeterminer) {

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
      componentDefinition.implementationInvoker.invokeMethod(resolvedParams, compilationDependencies, invocationContext)
    }

    override def transformOriginalInvocationResult(
        originalSource: Any,
        outputValidationContext: ValidationContext,
        typingResult: TypingResult,
        compilationDependencies: NodeCompilationDependencies
    ): Any = {
      val commonFormatRecords = scenarioTestData.commonFormatRecords(compilationDependencies.nodeId)
      lazy val watermarkStrategyOptions =
        originalSource.cast[WithWatermarkStrategyOptions].map(_.watermarkStrategyOptions)
      originalSource match {
        case sourceWithTestSupport: Source with FlinkSourceTestSupport[Object @unchecked]
            if commonFormatRecords.isEmpty =>
          logger.debug(
            s"Preparing source stubbed with test data in the source-specific format for component [${componentDefinition.id}]"
          )
          sourceSpecificFormatStubbedSourcePreparer.prepareStubbedSource(
            sourceWithTestSupport,
            typingResult,
            compilationDependencies.nodeId
          )
        case _ =>
          logger.debug(
            s"Preparing source stubbed with ${commonFormatRecords.size} test records in the common format for component [${componentDefinition.id}]"
          )
          CommonTestDataFormatStubbedSourcePreparer.prepareSubbedSource(
            commonFormatRecords,
            outputValidationContext,
            compilationDependencies.nodeId,
            watermarkStrategyOptions
          )
      }
    }

    private def resolveParam(param: Any): Any = param match {
      case lazyParameterCreator: EvaluableLazyParameterCreator[_] =>
        sourceSpecificFormatStubbedSourcePreparer.resolveParam(lazyParameterCreator)
      case other =>
        other
    }

  }

}

class TestFlinkExceptionHandler(
    metaData: MetaData,
    modelConfig: ModelConfig,
    listeners: Seq[ProcessListener],
    classLoader: ClassLoader
) extends FlinkExceptionHandler(metaData, modelConfig, listeners, classLoader) {

  override def restartStrategyConfig: Configuration =
    new Configuration()
      .set(RestartStrategyOptions.RESTART_STRATEGY, RestartStrategyType.NO_RESTART_STRATEGY.getMainValue)

  override val consumer: FlinkEspExceptionConsumer = _ => {}

}
