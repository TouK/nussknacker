package pl.touk.nussknacker.engine.definition.model

import pl.touk.nussknacker.engine.api.component.{ComponentAdditionalConfig, ComponentId, DesignerWideComponentId}
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.definition.component.Components.ComponentDefinitionExtractionMode
import pl.touk.nussknacker.engine.definition.component.ComponentsFromProvidersExtractor
import pl.touk.nussknacker.engine.modelconfig.ComponentsUiConfigParser

object ModelDefinitionExtractor {

  def extractModelDefinition(
      creator: ProcessConfigCreator,
      classLoader: ClassLoader,
      modelDependencies: ProcessObjectDependencies,
      // It won't be needed to pass category after we get rid of ProcessConfigCreator API
      category: Option[String],
      determineDesignerWideId: ComponentId => DesignerWideComponentId,
      additionalConfigsFromProvider: Map[DesignerWideComponentId, ComponentAdditionalConfig],
      componentDefinitionExtractionMode: ComponentDefinitionExtractionMode
  ): ModelDefinition = {
    val componentsUiConfig = ComponentsUiConfigParser.parse(modelDependencies.config)
    val modelDefinitionBasedOnConfigCreator =
      ModelDefinitionFromConfigCreatorExtractor.extractModelDefinition(
        creator,
        category,
        modelDependencies,
        componentsUiConfig,
        determineDesignerWideId,
        additionalConfigsFromProvider,
        componentDefinitionExtractionMode
      )
    val componentsFromProviders =
      ComponentsFromProvidersExtractor(classLoader).extractComponents(
        modelDependencies,
        componentsUiConfig,
        determineDesignerWideId,
        additionalConfigsFromProvider,
        componentDefinitionExtractionMode
      )
    modelDefinitionBasedOnConfigCreator.withComponents(componentsFromProviders)
  }

}
