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
import pl.touk.nussknacker.engine.component.ComponentsUiConfigExtractor
import pl.touk.nussknacker.engine.definition.DefinitionExtractor.ObjectWithMethodDef
import pl.touk.nussknacker.engine.definition.ProcessDefinitionExtractor.{
  CustomTransformerAdditionalData,
  ModelDefinitionWithTypes
}
import pl.touk.nussknacker.engine.definition.{GlobalVariableDefinitionExtractor, ProcessObjectDefinitionExtractor}
import pl.touk.nussknacker.engine.process.compiler.FlinkProcessCompiler
import pl.touk.nussknacker.engine.process.exception.FlinkExceptionHandler
import pl.touk.nussknacker.engine.util.test.TestExtensionsHolder

import scala.reflect.ClassTag

class FlinkProcessCompilerWithTestComponents(
    creator: ProcessConfigCreator,
    processConfig: Config,
    diskStateBackendSupport: Boolean,
    objectNaming: ObjectNaming,
    componentUseCase: ComponentUseCase,
    testExtensionsHolder: TestExtensionsHolder,
    testExceptionHolder: FlinkTestExceptionHolder,
) extends FlinkProcessCompiler(creator, processConfig, diskStateBackendSupport, objectNaming, componentUseCase) {

  override protected def definitions(
      processObjectDependencies: ProcessObjectDependencies,
      userCodeClassLoader: ClassLoader
  ): (ModelDefinitionWithTypes, EngineDictRegistry) = {
    val (definitionWithTypes, dictRegistry) = super.definitions(processObjectDependencies, userCodeClassLoader)
    val definitions                         = definitionWithTypes.modelDefinition
    val componentsUiConfig                  = ComponentsUiConfigExtractor.extract(processObjectDependencies.config)
    val testServicesDefs = ObjectWithMethodDef.forMap(
      testComponentsWithCategories[Service],
      ProcessObjectDefinitionExtractor.service,
      componentsUiConfig
    )
    val testCustomStreamTransformerDefs = ObjectWithMethodDef
      .forMap(
        testComponentsWithCategories[CustomStreamTransformer],
        ProcessObjectDefinitionExtractor.customStreamTransformer,
        componentsUiConfig
      )
      .map { case (name, el) =>
        val customStreamTransformer = el.obj.asInstanceOf[CustomStreamTransformer]
        val additionalData = CustomTransformerAdditionalData(
          customStreamTransformer.canHaveManyInputs,
          customStreamTransformer.canBeEnding
        )
        name -> (el, additionalData)
      }
    val testSourceDefs = ObjectWithMethodDef.forMap(
      testComponentsWithCategories[SourceFactory],
      ProcessObjectDefinitionExtractor.source,
      componentsUiConfig
    )
    val testSinkDefs = ObjectWithMethodDef.forMap(
      testComponentsWithCategories[SinkFactory],
      ProcessObjectDefinitionExtractor.sink,
      componentsUiConfig
    )
    val servicesWithTests                = definitions.services ++ testServicesDefs
    val sourcesWithTests                 = definitions.sourceFactories ++ testSourceDefs
    val sinksWithTests                   = definitions.sinkFactories ++ testSinkDefs
    val customStreamTransformerWithTests = definitions.customStreamTransformers ++ testCustomStreamTransformerDefs

    val expressionConfigWithTests = definitions.expressionConfig.copy(
      definitions.expressionConfig.globalVariables ++
        GlobalVariableDefinitionExtractor.extractDefinitions(
          testExtensionsHolder.globalVariables.view.map { case (key, value) =>
            key -> WithCategories.anyCategory(value)
          }.toMap
        )
    )

    val definitionsWithTestComponents = definitions.copy(
      services = servicesWithTests,
      sinkFactories = sinksWithTests,
      sourceFactories = sourcesWithTests,
      customStreamTransformers = customStreamTransformerWithTests,
      expressionConfig = expressionConfigWithTests
    )

    (ModelDefinitionWithTypes(definitionsWithTestComponents), dictRegistry)
  }

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
          testExceptionHolder.addException(exceptionInfo)
        }

      }
    case _ =>
      // additionally we forward errors to FlinkTestExceptionHolder
      val testExceptionListener = new EmptyProcessListener {
        override def exceptionThrown(exceptionInfo: NuExceptionInfo[_ <: Throwable]): Unit = {
          testExceptionHolder.addException(exceptionInfo)
        }
      }

      new FlinkExceptionHandler(metaData, processObjectDependencies, listeners :+ testExceptionListener, classLoader)
  }

  private def testComponentsWithCategories[T <: Component: ClassTag] =
    testExtensionsHolder.components[T].map(cd => cd.name -> WithCategories(cd.component.asInstanceOf[T])).toMap

  def this(
      testExtensionsHolder: TestExtensionsHolder,
      testExceptionHolder: FlinkTestExceptionHolder,
      modelData: ModelData,
      componentUseCase: ComponentUseCase
  ) = this(
    modelData.configCreator,
    modelData.processConfig,
    false,
    modelData.objectNaming,
    componentUseCase,
    testExtensionsHolder,
    testExceptionHolder
  )

}
