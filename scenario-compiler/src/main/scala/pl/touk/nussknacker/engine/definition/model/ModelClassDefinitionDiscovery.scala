package pl.touk.nussknacker.engine.definition.model

import pl.touk.nussknacker.engine.api.typed.typing.Typed
import pl.touk.nussknacker.engine.definition.clazz.{ClassDefinition, ClassDefinitionDiscovery, ClassDefinitionExtractor}

object ModelClassDefinitionDiscovery {

  // Extracts details of types (e.g. field definitions for variable suggestions) of extracted components.
  // We don't do it during component extraction because this is needed only in some cases (e.g. suggestions/validations) and it is costly
  def discoverClasses(definition: ModelDefinition): Set[ClassDefinition] = {
    val componentsAndGlobalVariables =
      definition.components ++ definition.expressionConfig.globalVariables.values
    val classesToExtractDefinitions = componentsAndGlobalVariables.flatMap(
      _.definedTypes
    ) ++ definition.expressionConfig.additionalClasses.map(Typed(_))
    new ClassDefinitionDiscovery(new ClassDefinitionExtractor(definition.settings))
      .discoverClassesFromTypes(classesToExtractDefinitions)
  }

}
