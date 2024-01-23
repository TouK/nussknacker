package pl.touk.nussknacker.engine.flink.util.test

import com.typesafe.config.Config
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.ModelData.ExtractDefinitionFun
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.component.ComponentId
import pl.touk.nussknacker.engine.api.namespaces.ObjectNaming
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.definition.component.ComponentDefinitionWithImplementation
import pl.touk.nussknacker.engine.definition.globalvariables.GlobalVariableDefinitionExtractor
import pl.touk.nussknacker.engine.definition.model.ModelDefinition
import pl.touk.nussknacker.engine.modelconfig.ComponentsUiConfig
import pl.touk.nussknacker.engine.process.compiler.{
  ComponentDefinitionContext,
  FlinkProcessCompilerDataFactory,
  TestFlinkExceptionHandler
}
import pl.touk.nussknacker.engine.process.exception.FlinkExceptionHandler
import pl.touk.nussknacker.engine.testmode.ResultsCollectingListener
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap

object FlinkProcessCompilerDataFactoryWithTestComponents {

  def apply(
      testExtensionsHolder: TestExtensionsHolder,
      resultsCollectingListener: ResultsCollectingListener,
      modelData: ModelData,
      componentUseCase: ComponentUseCase
  ): FlinkProcessCompilerDataFactory =
    FlinkProcessCompilerDataFactoryWithTestComponents(
      modelData.configCreator,
      modelData.extractModelDefinitionFun,
      modelData.modelConfig,
      modelData.objectNaming,
      componentUseCase,
      testExtensionsHolder,
      resultsCollectingListener
    )

  def apply(
      creator: ProcessConfigCreator,
      extractModelDefinition: ExtractDefinitionFun,
      modelConfig: Config,
      objectNaming: ObjectNaming,
      componentUseCase: ComponentUseCase,
      testExtensionsHolder: TestExtensionsHolder,
      resultsCollectingListener: ResultsCollectingListener,
  ): FlinkProcessCompilerDataFactory = {
    new FlinkProcessCompilerDataFactory(
      creator,
      extractModelDefinition,
      modelConfig,
      objectNaming,
      componentUseCase,
    ) {

      override protected def adjustDefinitions(
          originalModelDefinition: ModelDefinition,
          definitionContext: ComponentDefinitionContext
      ): ModelDefinition = {
        val testComponents =
          ComponentDefinitionWithImplementation.forList(
            components = testExtensionsHolder.components,
            additionalConfigs = ComponentsUiConfig.Empty,
            componentInfoToId = info => ComponentId(info.toString),
            additionalConfigsFromProvider = Map.empty
          )

        originalModelDefinition
          .withComponents(testComponents)
          .copy(
            expressionConfig = originalModelDefinition.expressionConfig.copy(
              originalModelDefinition.expressionConfig.globalVariables ++
                testExtensionsHolder.globalVariables.mapValuesNow(
                  GlobalVariableDefinitionExtractor.extractDefinition
                )
            )
          )
      }

      override protected def adjustListeners(
          defaults: List[ProcessListener],
          modelDependencies: ProcessObjectDependencies
      ): List[ProcessListener] = defaults :+ resultsCollectingListener

      override protected def exceptionHandler(
          metaData: MetaData,
          modelDependencies: ProcessObjectDependencies,
          listeners: Seq[ProcessListener],
          classLoader: ClassLoader
      ): FlinkExceptionHandler = componentUseCase match {
        case ComponentUseCase.TestRuntime => // We want to be consistent with exception handling in test mode, therefore we have disabled the default exception handler
          new TestFlinkExceptionHandler(metaData, modelDependencies, listeners, classLoader)
        case _ =>
          super.exceptionHandler(metaData, modelDependencies, listeners, classLoader)
      }

    }
  }

}
