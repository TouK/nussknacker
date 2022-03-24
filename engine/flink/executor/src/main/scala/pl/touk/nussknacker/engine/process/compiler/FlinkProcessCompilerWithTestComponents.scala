package pl.touk.nussknacker.engine.process.compiler

import com.typesafe.config.Config
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.Service
import pl.touk.nussknacker.engine.api.namespaces.ObjectNaming
import pl.touk.nussknacker.engine.api.process.{ComponentUseCase, ProcessConfigCreator, ProcessObjectDependencies, SinkFactory, Source, SourceFactory}
import pl.touk.nussknacker.engine.component.ComponentsUiConfigExtractor
import pl.touk.nussknacker.engine.definition.DefinitionExtractor.ObjectWithMethodDef
import pl.touk.nussknacker.engine.definition.ProcessDefinitionExtractor.ProcessDefinition
import pl.touk.nussknacker.engine.definition.ProcessObjectDefinitionExtractor
import pl.touk.nussknacker.engine.testmode.TestComponentHolder

class FlinkProcessCompilerWithTestComponents(creator: ProcessConfigCreator,
                                             processConfig: Config,
                                             diskStateBackendSupport: Boolean,
                                             objectNaming: ObjectNaming,
                                             componentUseCase: ComponentUseCase,
                                             testComponentsHolder: TestComponentHolder)
  extends FlinkProcessCompiler(creator, processConfig, diskStateBackendSupport, objectNaming, componentUseCase) {

  override protected def definitions(processObjectDependencies: ProcessObjectDependencies): ProcessDefinition[ObjectWithMethodDef] = {
    val definitions = super.definitions(processObjectDependencies)
    val componentsUiConfig = ComponentsUiConfigExtractor.extract(processObjectDependencies.config)
    val testServicesDefs = ObjectWithMethodDef.forMap(testComponentsHolder.components[Service], ProcessObjectDefinitionExtractor.service, componentsUiConfig)
    val testSourceDefs = ObjectWithMethodDef.forMap(testComponentsHolder.components[SourceFactory], ProcessObjectDefinitionExtractor.source, componentsUiConfig)
    val testSinkDefs = ObjectWithMethodDef.forMap(testComponentsHolder.components[SinkFactory], ProcessObjectDefinitionExtractor.sink, componentsUiConfig)
    val testCustomStreamTransformerDefs: Map[String, ObjectWithMethodDef] = ObjectWithMethodDef.forMap(testComponentsHolder.components, ProcessObjectDefinitionExtractor.customStreamTransformer, componentsUiConfig)
    val servicesWithTests = definitions.services ++ testServicesDefs
    val sourcesWithTests = definitions.sourceFactories ++ testSourceDefs
    val sinksWithTests = definitions.sinkFactories ++ testSinkDefs
    //not implemented completely, add additional data
    val customStreamTransformersWithTests = definitions.customStreamTransformers ++ testCustomStreamTransformerDefs
    val definitionsWithTestComponents = definitions.copy(services = servicesWithTests, sinkFactories = sinksWithTests, sourceFactories = sourcesWithTests)
    definitionsWithTestComponents
  }

  def this(componentsHolder: TestComponentHolder, modelData: ModelData) = this(modelData.configCreator, modelData.processConfig, false, modelData.objectNaming, ComponentUseCase.EngineRuntime, componentsHolder)
}
