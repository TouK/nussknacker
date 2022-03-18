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
import pl.touk.nussknacker.engine.testmode.MockComponentHolder

class MockedComponentsFlinkProcessCompiler(creator: ProcessConfigCreator,
                                           processConfig: Config,
                                           diskStateBackendSupport: Boolean,
                                           objectNaming: ObjectNaming,
                                           componentUseCase: ComponentUseCase,
                                           mockComponentsHolder: MockComponentHolder
                                          )
  extends FlinkProcessCompiler(creator, processConfig, diskStateBackendSupport, objectNaming, componentUseCase) {

  override protected def definitions(processObjectDependencies: ProcessObjectDependencies): ProcessDefinition[ObjectWithMethodDef] = {
    val definitions = super.definitions(processObjectDependencies)
    val componentsUiConfig = ComponentsUiConfigExtractor.extract(processObjectDependencies.config)
    val mockServicesDefs = ObjectWithMethodDef.forMap(mockComponentsHolder.components[Service], ProcessObjectDefinitionExtractor.service, componentsUiConfig)
    val mockSourceDefs = ObjectWithMethodDef.forMap(mockComponentsHolder.components[SourceFactory], ProcessObjectDefinitionExtractor.source, componentsUiConfig)
    val mockSinkDefs = ObjectWithMethodDef.forMap(mockComponentsHolder.components[SinkFactory], ProcessObjectDefinitionExtractor.sink, componentsUiConfig)
    val mockCustomStreamTransformerDefs: Map[String, ObjectWithMethodDef] = ObjectWithMethodDef.forMap(mockComponentsHolder.components, ProcessObjectDefinitionExtractor.customStreamTransformer, componentsUiConfig)
    val servicesWithMocks = definitions.services ++ mockServicesDefs
    val sourcesWithMocks = definitions.sourceFactories ++ mockSourceDefs
    val sinksWithMocks = definitions.sinkFactories ++ mockSinkDefs
    //not implemented completely, add additional data
    val customStreamTransformersWithMocks = definitions.customStreamTransformers ++ mockCustomStreamTransformerDefs
    val definitionsWithMockComponents = definitions.copy(services = servicesWithMocks, sinkFactories = sinksWithMocks, sourceFactories = sourcesWithMocks)
    definitionsWithMockComponents
  }

  def this(componentsHolder: MockComponentHolder, modelData: ModelData) = this(modelData.configCreator, modelData.processConfig, false, modelData.objectNaming, ComponentUseCase.Mock, componentsHolder)
}
