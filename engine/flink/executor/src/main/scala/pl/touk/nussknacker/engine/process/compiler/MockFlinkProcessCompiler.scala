package pl.touk.nussknacker.engine.process.compiler

import com.typesafe.config.Config
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.Service
import pl.touk.nussknacker.engine.api.namespaces.ObjectNaming
import pl.touk.nussknacker.engine.api.process.{ComponentUseCase, ProcessConfigCreator, ProcessObjectDependencies, WithCategories}
import pl.touk.nussknacker.engine.component.ComponentsUiConfigExtractor
import pl.touk.nussknacker.engine.definition.DefinitionExtractor.ObjectWithMethodDef
import pl.touk.nussknacker.engine.definition.ProcessDefinitionExtractor.ProcessDefinition
import pl.touk.nussknacker.engine.definition.ProcessObjectDefinitionExtractor
import pl.touk.nussknacker.engine.graph.EspProcess

class MockFlinkProcessCompiler(scenario: EspProcess,
                               creator: ProcessConfigCreator,
                               processConfig: Config,
                               diskStateBackendSupport: Boolean,
                               objectNaming: ObjectNaming,
                               componentUseCase: ComponentUseCase,
                               mockServices: Map[String, WithCategories[Service]]
                              )
  extends FlinkProcessCompiler(creator, processConfig, diskStateBackendSupport, objectNaming, componentUseCase) {


  override protected def definitions(processObjectDependencies: ProcessObjectDependencies): ProcessDefinition[ObjectWithMethodDef] = {
    val definitions = super.definitions(processObjectDependencies)
    val componentsUiConfig = ComponentsUiConfigExtractor.extract(processObjectDependencies.config)
    val mockServicesDefs = ObjectWithMethodDef.forMap(mockServices, ProcessObjectDefinitionExtractor.service, componentsUiConfig)
    val servicesWithMocks = definitions.services ++ mockServicesDefs
    val mockDefinitions = definitions.copy(services = servicesWithMocks)
    mockDefinitions
  }

  //todo for now its only services
  def this(services: Map[String, WithCategories[Service]], scenario: EspProcess, modelData: ModelData) = this(scenario, modelData.configCreator, modelData.processConfig, false, modelData.objectNaming, ComponentUseCase.Mock, services)
}
