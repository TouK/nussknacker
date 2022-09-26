package pl.touk.nussknacker.engine.flink.util.test

import com.typesafe.config.Config
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.component.Component
import pl.touk.nussknacker.engine.api.namespaces.ObjectNaming
import pl.touk.nussknacker.engine.api.process.{ComponentUseCase, ProcessConfigCreator, ProcessObjectDependencies, SinkFactory, SourceFactory, WithCategories}
import pl.touk.nussknacker.engine.api.{CustomStreamTransformer, Service}
import pl.touk.nussknacker.engine.component.ComponentsUiConfigExtractor
import pl.touk.nussknacker.engine.definition.DefinitionExtractor.ObjectWithMethodDef
import pl.touk.nussknacker.engine.definition.ProcessDefinitionExtractor.ProcessDefinition
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


  override protected def definitions(processObjectDependencies: ProcessObjectDependencies): ProcessDefinition[ObjectWithMethodDef] = {
    val definitions = super.definitions(processObjectDependencies)
    val componentsUiConfig = ComponentsUiConfigExtractor.extract(processObjectDependencies.config)
    val testServicesDefs = ObjectWithMethodDef.forMap(testComponentsWithCategories[Service], ProcessObjectDefinitionExtractor.service, componentsUiConfig)
    val testSourceDefs = ObjectWithMethodDef.forMap(testComponentsWithCategories[SourceFactory], ProcessObjectDefinitionExtractor.source, componentsUiConfig)
    val testSinkDefs = ObjectWithMethodDef.forMap(testComponentsWithCategories[SinkFactory], ProcessObjectDefinitionExtractor.sink, componentsUiConfig)
    val testCustomStreamTransformerDefs: Map[String, ObjectWithMethodDef] = ObjectWithMethodDef.forMap(testComponentsWithCategories[CustomStreamTransformer], ProcessObjectDefinitionExtractor.customStreamTransformer, componentsUiConfig)
    val servicesWithTests = definitions.services ++ testServicesDefs
    val sourcesWithTests = definitions.sourceFactories ++ testSourceDefs
    val sinksWithTests = definitions.sinkFactories ++ testSinkDefs
    //not implemented completely, add additional data
    val customStreamTransformersWithTests = definitions.customStreamTransformers ++ testCustomStreamTransformerDefs
    val definitionsWithTestComponents = definitions.copy(services = servicesWithTests, sinkFactories = sinksWithTests, sourceFactories = sourcesWithTests)
    definitionsWithTestComponents
  }

  private def testComponentsWithCategories[T <: Component : ClassTag] = testComponentsHolder.components[T].map(cd => cd.name -> WithCategories(cd.component.asInstanceOf[T])).toMap

  def this(componentsHolder: TestComponentsHolder, modelData: ModelData) = this(modelData.configCreator, modelData.processConfig, false, modelData.objectNaming, ComponentUseCase.EngineRuntime, componentsHolder)
}
