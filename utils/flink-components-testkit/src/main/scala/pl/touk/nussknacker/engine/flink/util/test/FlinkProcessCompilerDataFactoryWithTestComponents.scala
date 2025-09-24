package pl.touk.nussknacker.engine.flink.util.test

import pl.touk.nussknacker.engine.{ModelConfig, ModelData, RuntimeMode}
import pl.touk.nussknacker.engine.ModelConfig.GlobalParametersConfig
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.component.{ComponentType, DesignerWideComponentId, NodesDeploymentData}
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.livedata.DataRecord
import pl.touk.nussknacker.engine.api.typed.typing
import pl.touk.nussknacker.engine.compile.nodecompilation.StaticComponentOutputValidationContextDeterminer
import pl.touk.nussknacker.engine.definition.component.{
  ComponentDefinitionWithImplementation,
  NodeCompilationDependencies
}
import pl.touk.nussknacker.engine.definition.globalvariables.GlobalVariableDefinitionWithImplementation
import pl.touk.nussknacker.engine.definition.model.ModelDefinition
import pl.touk.nussknacker.engine.modelconfig.ComponentsUiConfig
import pl.touk.nussknacker.engine.process.compiler.{ComponentDefinitionContext, FlinkProcessCompilerDataFactory}
import pl.touk.nussknacker.engine.process.exception.FlinkExceptionHandler
import pl.touk.nussknacker.engine.process.scenariotesting.{
  DataRecordsSource,
  StubbedComponentImplementationInvoker,
  TestFlinkExceptionHandler
}
import pl.touk.nussknacker.engine.testmode.NonMapBasedRecordTypesOps.TypingResultExt
import pl.touk.nussknacker.engine.testmode.ResultsCollectingListener
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap
import pl.touk.nussknacker.engine.util.test.TestDataRecord
import pl.touk.nussknacker.engine.util.watermarkstrategy.WithWatermarkStrategyOptions
import pl.touk.nussknacker.engine.variables.GlobalVariablesPreparer
import shapeless.syntax.typeable.typeableOps

object FlinkProcessCompilerDataFactoryWithTestComponents {

  def apply(
      testExtensionsHolder: TestExtensionsHolder,
      resultsCollectingListener: ResultsCollectingListener[Any],
      modelData: ModelData,
      runtimeMode: RuntimeMode,
      testRecordsOpt: Option[List[TestDataRecord]],
      nodesData: NodesDeploymentData,
  ): FlinkProcessCompilerDataFactory = {
    new FlinkProcessCompilerDataFactory(
      creator = modelData.configCreator,
      extractModelDefinition = modelData.extractModelDefinitionFun,
      modelConfig = modelData.modelConfig,
      runtimeMode = runtimeMode,
      configsFromProviderWithDictionaryEditor = modelData.additionalConfigsFromProvider,
      nodesData = nodesData,
      processListeners = List.empty,
    ) {

      override protected def adjustDefinitions(
          originalModelDefinition: ModelDefinition,
          definitionContext: ComponentDefinitionContext,
      ): ModelDefinition = {
        val testComponents =
          ComponentDefinitionWithImplementation.forList(
            components = testExtensionsHolder.components,
            additionalConfigs = ComponentsUiConfig.Empty,
            determineDesignerWideId = id => DesignerWideComponentId(id.toString),
            additionalConfigsFromProvider = Map.empty,
            globalParametersConfig = GlobalParametersConfig.default
          )

        val withTestComponents = originalModelDefinition.withComponents(testComponents)

        val withStubbedSourcesIfNeeded = testRecordsOpt match {
          case None =>
            withTestComponents
          case Some(testRecords) =>
            val outputValidationContextDeterminer = new StaticComponentOutputValidationContextDeterminer(
              GlobalVariablesPreparer(definitionContext.modelDefinitionWithClasses.modelDefinition.expressionConfig)
            )

            withTestComponents.copy(components =
              withTestComponents.components.copy(components =
                withTestComponents.components.components.map {
                  case component if component.componentType == ComponentType.Source =>
                    component.withImplementationInvoker(
                      new StubbedComponentImplementationInvoker(component, outputValidationContextDeterminer) {
                        override def transformOriginalInvocationResult(
                            originalSource: Any,
                            outputValidationContext: ValidationContext,
                            typingResult: typing.TypingResult,
                            compilationDependencies: NodeCompilationDependencies,
                        ): Any = { // We change record type because to emulate the same behaviour as we have during scenario testing
                          val outputValidationContextWithRecordsAsMaps =
                            outputValidationContext.mapTypes(_.withoutValue.toMapBasedRecordTypes)
                          val recordsForSource =
                            testRecords
                              .filter(_.sourceId == compilationDependencies.nodeId)
                              .sortBy(_.upstreamTimestamp)
                              .map(r => DataRecord(r.variables, r.upstreamTimestamp))
                          new DataRecordsSource(
                            recordsForSource,
                            outputValidationContextWithRecordsAsMaps,
                            originalSource.cast[WithWatermarkStrategyOptions].map(_.watermarkStrategyOptions)
                          )
                        }
                      }
                    )
                  case other => other
                }
              )
            )
        }

        withStubbedSourcesIfNeeded
          .copy(
            expressionConfig = originalModelDefinition.expressionConfig.copy(
              originalModelDefinition.expressionConfig.globalVariables ++
                testExtensionsHolder.globalVariables.mapValuesNow(
                  GlobalVariableDefinitionWithImplementation(_)
                )
            )
          )
      }

      override protected def adjustListeners(defaults: List[ProcessListener]): List[ProcessListener] =
        defaults :+ resultsCollectingListener

      override protected def exceptionHandler(
          metaData: MetaData,
          modelConfig: ModelConfig,
          listeners: Seq[ProcessListener],
          classLoader: ClassLoader
      ): FlinkExceptionHandler = runtimeMode match {
        case RuntimeMode.Test => // We want to be consistent with exception handling in test mode, therefore we have disabled the default exception handler
          new TestFlinkExceptionHandler(metaData, modelConfig, listeners, classLoader)
        case _ =>
          super.exceptionHandler(metaData, modelConfig, listeners, classLoader)
      }

    }
  }

}
