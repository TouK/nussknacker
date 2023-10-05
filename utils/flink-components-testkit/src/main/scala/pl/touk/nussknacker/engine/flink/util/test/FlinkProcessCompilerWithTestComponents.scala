package pl.touk.nussknacker.engine.flink.util.test

import com.typesafe.config.Config
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.component.Component
import pl.touk.nussknacker.engine.api.dict.EngineDictRegistry
import pl.touk.nussknacker.engine.api.namespaces.ObjectNaming
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.api.{CustomStreamTransformer, Service}
import pl.touk.nussknacker.engine.component.ComponentsUiConfigExtractor
import pl.touk.nussknacker.engine.definition.DefinitionExtractor.ObjectWithMethodDef
import pl.touk.nussknacker.engine.definition.ProcessDefinitionExtractor.{CustomTransformerAdditionalData, ModelDefinitionWithTypes}
import pl.touk.nussknacker.engine.definition.ProcessObjectDefinitionExtractor
import pl.touk.nussknacker.engine.process.compiler.FlinkProcessCompiler
import pl.touk.nussknacker.engine.util.test.TestComponentsHolder

import scala.reflect.ClassTag

class FlinkProcessCompilerWithTestComponents(creator: ProcessConfigCreator,
                                             processConfig: Config,
                                             diskStateBackendSupport: Boolean,
                                             objectNaming: ObjectNaming,
                                             componentUseCase: ComponentUseCase,
                                             testComponentsHolder: TestComponentsHolder)
  extends FlinkProcessCompiler(creator, processConfig, diskStateBackendSupport, objectNaming, componentUseCase) {


  override protected def definitions(processObjectDependencies: ProcessObjectDependencies,
                                     userCodeClassLoader: ClassLoader): (ModelDefinitionWithTypes, EngineDictRegistry) = {
    val (definitionWithTypes, dictRegistry) = super.definitions(processObjectDependencies, userCodeClassLoader)
    val definitions = definitionWithTypes.modelDefinition
    val componentsUiConfig = ComponentsUiConfigExtractor.extract(processObjectDependencies.config)
    val testServicesDefs = ObjectWithMethodDef.forMap(testComponentsWithCategories[Service], ProcessObjectDefinitionExtractor.service, componentsUiConfig)
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
    val testSourceDefs = ObjectWithMethodDef.forMap(testComponentsWithCategories[SourceFactory], ProcessObjectDefinitionExtractor.source, componentsUiConfig)
    val testSinkDefs = ObjectWithMethodDef.forMap(testComponentsWithCategories[SinkFactory], ProcessObjectDefinitionExtractor.sink, componentsUiConfig)
    val servicesWithTests = definitions.services ++ testServicesDefs
    val sourcesWithTests = definitions.sourceFactories ++ testSourceDefs
    val sinksWithTests = definitions.sinkFactories ++ testSinkDefs
    val customStreamTransformerWithTests = definitions.customStreamTransformers ++ testCustomStreamTransformerDefs

    val definitionsWithTestComponents = definitions.copy(
      services = servicesWithTests,
      sinkFactories = sinksWithTests,
      sourceFactories = sourcesWithTests,
      customStreamTransformers = customStreamTransformerWithTests
    )

    (ModelDefinitionWithTypes(definitionsWithTestComponents), dictRegistry)
  }

  private def testComponentsWithCategories[T <: Component : ClassTag] = testComponentsHolder.components[T].map(cd => cd.name -> WithCategories(cd.component.asInstanceOf[T])).toMap

  def this(componentsHolder: TestComponentsHolder, modelData: ModelData, componentUseCase: ComponentUseCase) = this(modelData.configCreator, modelData.processConfig, false, modelData.objectNaming, componentUseCase, componentsHolder)
}
