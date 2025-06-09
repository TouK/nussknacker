package pl.touk.nussknacker.engine.process.scenariotesting

import cats.data.NonEmptyList
import cats.data.Validated.Valid
import com.typesafe.scalalogging.LazyLogging
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
import pl.touk.nussknacker.engine.api.context.{ContextTransformation, ScenarioCompilationErrors, ValidationContext}
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
import pl.touk.nussknacker.engine.definition.component.ComponentImplementationInvoker.{
  ComponentImplementationSpecificInvocationContext,
  DynamicComponentInvocationContext
}
import pl.touk.nussknacker.engine.definition.component.dynamic.DynamicComponentDefinitionWithImplementation
import pl.touk.nussknacker.engine.definition.component.methodbased.MethodBasedComponentDefinitionWithImplementation
import pl.touk.nussknacker.engine.definition.fragment.FragmentParametersDefinitionExtractor
import pl.touk.nussknacker.engine.definition.model.ModelDefinition
import pl.touk.nussknacker.engine.flink.api.exception.FlinkEspExceptionConsumer
import pl.touk.nussknacker.engine.flink.api.process.{FlinkSource, FlinkSourceTestSupport}
import pl.touk.nussknacker.engine.flink.util.source.EmptySource
import pl.touk.nussknacker.engine.graph.node.FragmentInputDefinition
import pl.touk.nussknacker.engine.process.compiler.{ComponentDefinitionContext, FlinkProcessCompilerDataFactory}
import pl.touk.nussknacker.engine.process.exception.FlinkExceptionHandler
import pl.touk.nussknacker.engine.testmode.{ResultsCollectingListener, TestDataPreparer}
import pl.touk.nussknacker.engine.variables.GlobalVariablesPreparer

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
      componentDefinition.implementationInvoker.invokeMethod(resolvedParams, compilationDependencies, invocationContext)
    }

    override def transformOriginalInvocationResult(
        originalSource: Any,
        originalSourceWasWrappedInContextTransformation: Boolean,
        typingResult: TypingResult,
        compilationDependencies: NodeCompilationDependencies,
        invocationContext: Option[ComponentImplementationSpecificInvocationContext]
    ): Any = {
      val outputValidationContext =
        determineOutputValidationContext(originalSource, compilationDependencies, invocationContext)

      val commonFormatRecordsNelOpt =
        NonEmptyList.fromList(scenarioTestData.commonFormatRecords(compilationDependencies.nodeId))
      (originalSource, commonFormatRecordsNelOpt) match {
        case (sourceWithTestSupport: Source with FlinkSourceTestSupport[Object @unchecked], None) =>
          sourceSpecificFormatStubbedSourcePreparer.prepareStubbedSource(
            sourceWithTestSupport,
            typingResult,
            compilationDependencies.nodeId
          )
        case (_, Some(commonFormatRecordsNel)) =>
          val stubbedSource = CommonTestDataFormatStubbedSourcePreparer.prepareSubbedSource(
            commonFormatRecordsNel,
            outputValidationContext
          )
          recoverOutputValidationContextIfNeeded(
            originalSourceWasWrappedInContextTransformation,
            outputValidationContext,
            stubbedSource
          )
        case _ =>
          // TODO: This probably doesn't work correctly for sources with custom ContextInitializer -
          //       we should recover validation context, see TestFlinkProcessCompilerDataFactory.recoverOutputValidationContextIfNeeded
          EmptySource(typingResult)
      }
    }

    private def resolveParam(param: Any): Any = param match {
      case lazyParameterCreator: EvaluableLazyParameterCreator[_] =>
        sourceSpecificFormatStubbedSourcePreparer.resolveParam(lazyParameterCreator)
      case other =>
        other
    }

    private def determineOutputValidationContext(
        originalSource: Any,
        compilationDependencies: NodeCompilationDependencies,
        invocationContext: Option[ComponentImplementationSpecificInvocationContext]
    ) = {
      (componentDefinition, invocationContext) match {
        case (_, Some(DynamicComponentInvocationContext(_, outputValidationContext))) => outputValidationContext
        case (staticComponent: MethodBasedComponentDefinitionWithImplementation, _) =>
          outputValidationContextDeterminer
            .contextAfterNode(
              nodeData = compilationDependencies.nodeData,
              customNodeIsEndingNode = None,
              staticComponent = staticComponent,
              validComponentExecutor = Valid(originalSource),
              inputContext = compilationDependencies.inputValidationContext
            )(compilationDependencies.jobData)
            .valueOr { errNel =>
              throw new IllegalStateException(
                "Compilation errors during output validation context determining",
                ScenarioCompilationErrors(errNel.toList)
              )
            }
        case _ =>
          throw new IllegalStateException(
            s"Illegal combination of component [$componentDefinition] and invocation context [$invocationContext]"
          )
      }
    }

    private def recoverOutputValidationContextIfNeeded(
        originalSourceWasWrappedInContextTransformation: Boolean,
        outputValidationContext: ValidationContext,
        stubbedSource: FlinkSource
    ) = {
      componentDefinition match {
        case _: DynamicComponentDefinitionWithImplementation => stubbedSource
        case _: MethodBasedComponentDefinitionWithImplementation if originalSourceWasWrappedInContextTransformation =>
          stubbedSource
        case _: MethodBasedComponentDefinitionWithImplementation =>
          // For static components that returns Source, we have to recover the output validation context
          ContextTransformation
            .definedBy(_ => Valid(outputValidationContext))
            .implementedBy(stubbedSource)
      }
    }

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
