package pl.touk.nussknacker.engine.definition.model

import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.definition.component.{
  ComponentDefinitionWithImplementation,
  ComponentsFromProvidersExtractor
}

object ModelDefinitionExtractor {

  def extractModelDefinition(
      creator: ProcessConfigCreator,
      classLoader: ClassLoader,
      processObjectDependencies: ProcessObjectDependencies,
      // It won't be needed to pass category after we get rid of ProcessConfigCreator API
      category: Option[String]
  ): ModelDefinition[ComponentDefinitionWithImplementation] = {
    val modelDefinitionBasedOnConfigCreator =
      ModelDefinitionFromConfigCreatorExtractor.extractModelDefinition(creator, processObjectDependencies, category)
    val componentsFromProviders =
      ComponentsFromProvidersExtractor(classLoader).extractComponents(processObjectDependencies)
    modelDefinitionBasedOnConfigCreator.addComponents(componentsFromProviders)
  }

}
