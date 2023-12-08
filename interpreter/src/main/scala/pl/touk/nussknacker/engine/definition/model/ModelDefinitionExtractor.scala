package pl.touk.nussknacker.engine.definition.model

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
      processObjectDependencies: ProcessObjectDependencies,
      // It won't be needed to pass category after we get rid of ProcessConfigCreator API
      category: Option[String]
  ): ModelDefinition[ComponentDefinitionWithImplementation] = {
    val componentsUiConfig = ComponentsUiConfigParser.parse(processObjectDependencies.config)
    val modelDefinitionBasedOnConfigCreator =
      ModelDefinitionFromConfigCreatorExtractor.extractModelDefinition(
        creator,
        category,
        processObjectDependencies,
        componentsUiConfig
      )
    val componentsFromProviders =
      ComponentsFromProvidersExtractor(classLoader).extractComponents(processObjectDependencies, componentsUiConfig)
    modelDefinitionBasedOnConfigCreator.addComponents(componentsFromProviders)
  }

}
