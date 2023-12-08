package pl.touk.nussknacker.engine.definition.model

import pl.touk.nussknacker.engine.api.CustomStreamTransformer
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.definition.component.{
  ComponentDefinitionWithImplementation,
  ComponentFromProvidersExtractor,
  CustomComponentSpecificData
}
import pl.touk.nussknacker.engine.definition.globalvariables.{ExpressionDefinition, GlobalVariableDefinitionExtractor}
import pl.touk.nussknacker.engine.modelconfig.ComponentsUiConfigParser

object ModelDefinitionExtractor {

  import pl.touk.nussknacker.engine.util.Implicits._

  // TODO: enable passing components directly, without ComponentProvider discovery, e.g. for testing
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
      ComponentFromProvidersExtractor(classLoader).extractComponents(processObjectDependencies)
    modelDefinitionBasedOnConfigCreator.addComponents(componentsFromProviders)
  }

}
