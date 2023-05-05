package pl.touk.nussknacker.engine.flink.util.test

import com.typesafe.config.Config
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.component.Component
import pl.touk.nussknacker.engine.api.process.{ComponentUseCase, SinkFactory, SourceFactory, WithCategories}
import pl.touk.nussknacker.engine.api.{CustomStreamTransformer, Service}
import pl.touk.nussknacker.engine.component.ComponentsUiConfigExtractor
import pl.touk.nussknacker.engine.definition.DefinitionExtractor.ObjectWithMethodDef
import pl.touk.nussknacker.engine.definition.ProcessDefinitionExtractor.{ModelDefinitionWithTypes, ProcessDefinition}
import pl.touk.nussknacker.engine.definition.ProcessObjectDefinitionExtractor
import pl.touk.nussknacker.engine.process.compiler.FlinkProcessCompiler
import pl.touk.nussknacker.engine.util.test.TestComponentsHolder

import scala.reflect.ClassTag

object FlinkProcessCompilerWithTestComponents {

  def apply(componentsHolder: TestComponentsHolder, modelData: ModelData): FlinkProcessCompiler = {
    val modelDefinition = ModelDefinitionWithTypes(definitions(modelData.modelDefinitionWithTypes.modelDefinition, modelData.processConfig, componentsHolder))
    new FlinkProcessCompiler(modelDefinition, modelData.configCreator, modelData.processConfig, diskStateBackendSupport = false, modelData.objectNaming, ComponentUseCase.EngineRuntime)
  }

  private def definitions(definitions: ProcessDefinition[ObjectWithMethodDef],
                          processConfig: Config,
                          testComponentsHolder: TestComponentsHolder): ProcessDefinition[ObjectWithMethodDef] = {
    def testComponentsWithCategories[T <: Component : ClassTag] = testComponentsHolder.components[T].map(cd => cd.name -> WithCategories(cd.component.asInstanceOf[T])).toMap
    val componentsUiConfig = ComponentsUiConfigExtractor.extract(processConfig)
    val testServicesDefs = ObjectWithMethodDef.forMap(testComponentsWithCategories[Service], ProcessObjectDefinitionExtractor.service, componentsUiConfig)
    val testSourceDefs = ObjectWithMethodDef.forMap(testComponentsWithCategories[SourceFactory], ProcessObjectDefinitionExtractor.source, componentsUiConfig)
    val testSinkDefs = ObjectWithMethodDef.forMap(testComponentsWithCategories[SinkFactory], ProcessObjectDefinitionExtractor.sink, componentsUiConfig)
    val testCustomStreamTransformerDefs: Map[String, ObjectWithMethodDef] = ObjectWithMethodDef.forMap(testComponentsWithCategories[CustomStreamTransformer], ProcessObjectDefinitionExtractor.customStreamTransformer, componentsUiConfig)
    val servicesWithTests = definitions.services ++ testServicesDefs
    val sourcesWithTests = definitions.sourceFactories ++ testSourceDefs
    val sinksWithTests = definitions.sinkFactories ++ testSinkDefs
    //FIXME: not implemented completely, add additional data
    val customStreamTransformersWithTests = definitions.customStreamTransformers ++ testCustomStreamTransformerDefs
    definitions.copy(
      services = servicesWithTests,
      sinkFactories = sinksWithTests,
      sourceFactories = sourcesWithTests)
  }

}
