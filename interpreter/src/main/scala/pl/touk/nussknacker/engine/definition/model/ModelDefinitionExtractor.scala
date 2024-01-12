package pl.touk.nussknacker.engine.definition.model

import pl.touk.nussknacker.engine.api.component.{ComponentAdditionalConfig, ComponentId, ComponentInfo}
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.definition.component.{
  ComponentDefinitionWithImplementation,
  ComponentsFromProvidersExtractor
}
import pl.touk.nussknacker.engine.modelconfig.ComponentsUiConfigParser

object ModelDefinitionExtractor {

  def extractModelDefinition(
      creator: ProcessConfigCreator,
      classLoader: ClassLoader,
      modelDependencies: ProcessObjectDependencies,
      // It won't be needed to pass category after we get rid of ProcessConfigCreator API
      category: Option[String],
      componentInfoToId: ComponentInfo => ComponentId,
      additionalConfigsFromProvider: Map[ComponentId, ComponentAdditionalConfig]
  ): ModelDefinition[ComponentDefinitionWithImplementation] = {
    val componentsUiConfig = ComponentsUiConfigParser.parse(modelDependencies.config)
    val modelDefinitionBasedOnConfigCreator =
      ModelDefinitionFromConfigCreatorExtractor.extractModelDefinition(
        creator,
        category,
        modelDependencies,
        componentsUiConfig,
        componentInfoToId,
        additionalConfigsFromProvider
      )
    val componentsFromProviders =
      ComponentsFromProvidersExtractor(classLoader).extractComponents(
        modelDependencies,
        componentsUiConfig,
        componentInfoToId,
        additionalConfigsFromProvider
      )
    modelDefinitionBasedOnConfigCreator.withComponents(componentsFromProviders)
  }

}
