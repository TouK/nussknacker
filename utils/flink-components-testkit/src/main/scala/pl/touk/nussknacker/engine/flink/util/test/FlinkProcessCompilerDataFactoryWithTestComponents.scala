package pl.touk.nussknacker.engine.flink.util.test

import com.typesafe.config.Config
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.ModelData.ExtractDefinitionFun
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.dict.EngineDictRegistry
import pl.touk.nussknacker.engine.api.namespaces.ObjectNaming
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.definition.component.ComponentDefinitionWithImplementation
import pl.touk.nussknacker.engine.definition.globalvariables.GlobalVariableDefinitionExtractor
import pl.touk.nussknacker.engine.definition.model.ModelDefinitionWithClasses
import pl.touk.nussknacker.engine.modelconfig.ComponentsUiConfigParser
import pl.touk.nussknacker.engine.process.compiler.{FlinkProcessCompilerDataFactory, TestFlinkExceptionHandler}
import pl.touk.nussknacker.engine.process.exception.FlinkExceptionHandler
import pl.touk.nussknacker.engine.testmode.ResultsCollectingListener

class FlinkProcessCompilerDataFactoryWithTestComponents(
    creator: ProcessConfigCreator,
    extractModelDefinition: ExtractDefinitionFun,
    modelConfig: Config,
    objectNaming: ObjectNaming,
    componentUseCase: ComponentUseCase,
    testExtensionsHolder: TestExtensionsHolder,
    resultsCollectingListener: ResultsCollectingListener,
) extends FlinkProcessCompilerDataFactory(
      creator,
      extractModelDefinition,
      modelConfig,
      objectNaming,
      componentUseCase,
    ) {

  override protected def definitions(
      processObjectDependencies: ProcessObjectDependencies,
      userCodeClassLoader: ClassLoader
  ): (ModelDefinitionWithClasses, EngineDictRegistry) = {
    val (definitionWithTypes, dictRegistry) = super.definitions(processObjectDependencies, userCodeClassLoader)
    val definitions                         = definitionWithTypes.modelDefinition
    val componentsUiConfig                  = ComponentsUiConfigParser.parse(processObjectDependencies.config)
    val testComponents =
      ComponentDefinitionWithImplementation.forList(testExtensionsHolder.components, componentsUiConfig)

    val definitionsWithTestComponentsAndGlobalVariables = definitions
      .withComponents(testComponents)
      .copy(
        expressionConfig = definitions.expressionConfig.copy(
          definitions.expressionConfig.globalVariables ++
            GlobalVariableDefinitionExtractor.extractDefinitions(
              testExtensionsHolder.globalVariables.view.map { case (key, value) =>
                key -> WithCategories.anyCategory(value)
              }.toMap
            )
        )
      )

    (ModelDefinitionWithClasses(definitionsWithTestComponentsAndGlobalVariables), dictRegistry)
  }

  override protected def adjustListeners(
      defaults: List[ProcessListener],
      processObjectDependencies: ProcessObjectDependencies
  ): List[ProcessListener] = defaults :+ resultsCollectingListener

  override protected def exceptionHandler(
      metaData: MetaData,
      processObjectDependencies: ProcessObjectDependencies,
      listeners: Seq[ProcessListener],
      classLoader: ClassLoader
  ): FlinkExceptionHandler = componentUseCase match {
    case ComponentUseCase.TestRuntime => // We want to be consistent with exception handling in test mode, therefore we have disabled the default exception handler
      new TestFlinkExceptionHandler(metaData, processObjectDependencies, listeners, classLoader)
    case _ =>
      super.exceptionHandler(metaData, processObjectDependencies, listeners, classLoader)
  }

  def this(
      testExtensionsHolder: TestExtensionsHolder,
      resultsCollectingListener: ResultsCollectingListener,
      modelData: ModelData,
      componentUseCase: ComponentUseCase
  ) = this(
    modelData.configCreator,
    modelData.extractModelDefinitionFun,
    modelData.modelConfig,
    modelData.objectNaming,
    componentUseCase,
    testExtensionsHolder,
    resultsCollectingListener
  )

}
