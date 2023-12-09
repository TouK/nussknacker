package pl.touk.nussknacker.engine.flink.util.test

import com.typesafe.config.Config
import org.apache.flink.api.common.restartstrategy.RestartStrategies
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.component.Component
import pl.touk.nussknacker.engine.api.dict.EngineDictRegistry
import pl.touk.nussknacker.engine.api.exception.NuExceptionInfo
import pl.touk.nussknacker.engine.api.namespaces.ObjectNaming
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.definition.component.{
  ComponentDefinitionWithImplementation,
  CustomComponentSpecificData
}
import pl.touk.nussknacker.engine.definition.globalvariables.GlobalVariableDefinitionExtractor
import pl.touk.nussknacker.engine.definition.model.ModelDefinitionWithClasses
import pl.touk.nussknacker.engine.modelconfig.ComponentsUiConfigParser
import pl.touk.nussknacker.engine.process.compiler.FlinkProcessCompiler
import pl.touk.nussknacker.engine.process.exception.FlinkExceptionHandler
import pl.touk.nussknacker.engine.testmode.ResultsCollectingListener

import scala.reflect.ClassTag

class FlinkProcessCompilerWithTestComponents(
    modelData: ModelData,
    creator: ProcessConfigCreator,
    modelConfig: Config,
    diskStateBackendSupport: Boolean,
    objectNaming: ObjectNaming,
    componentUseCase: ComponentUseCase,
    testExtensionsHolder: TestExtensionsHolder,
    resultsCollectingListener: ResultsCollectingListener,
) extends FlinkProcessCompiler(
      creator,
      modelConfig,
      diskStateBackendSupport,
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
    val testComponents = ComponentDefinitionWithImplementation.forList(
      testComponentsWithCategories,
      componentsUiConfig
    )
    val definitionsWithTestComponentsAndGlobalVariables = definitions
      .addComponents(testComponents)
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
      new FlinkExceptionHandler(metaData, processObjectDependencies, listeners, classLoader) {
        override def restartStrategy: RestartStrategies.RestartStrategyConfiguration =
          RestartStrategies.noRestart()

        override def handle(exceptionInfo: NuExceptionInfo[_ <: Throwable]): Unit = {
          resultsCollectingListener.exceptionThrown(exceptionInfo)
        }

      }
    case _ =>
      new FlinkExceptionHandler(
        metaData,
        processObjectDependencies,
        listeners,
        classLoader
      )
  }

  private def testComponentsWithCategories =
    testExtensionsHolder.components
      .map(cd => cd.name -> WithCategories.anyCategory(cd.component))

  def this(
      testExtensionsHolder: TestExtensionsHolder,
      resultsCollectingListener: ResultsCollectingListener,
      modelData: ModelData,
      componentUseCase: ComponentUseCase
  ) = this(
    modelData,
    modelData.configCreator,
    modelData.modelConfig,
    false,
    modelData.objectNaming,
    componentUseCase,
    testExtensionsHolder,
    resultsCollectingListener
  )

}
