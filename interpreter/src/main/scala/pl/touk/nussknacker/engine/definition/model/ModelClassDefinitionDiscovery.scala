package pl.touk.nussknacker.engine.definition.model

import pl.touk.nussknacker.engine.api.typed.typing.Typed
import pl.touk.nussknacker.engine.definition.clazz.{ClassDefinition, ClassDefinitionDiscovery}
import pl.touk.nussknacker.engine.definition.component.{
  ComponentClassDefinitionDiscovery,
  ComponentDefinitionWithImplementation
}

object ModelClassDefinitionDiscovery {

  // TODO: We should pass collectedSoFar in every to avoid traversing the same types more than once
  // Extracts details of types (e.g. field definitions for variable suggestions) of extracted components.
  // We don't do it during component extraction because this is needed only in some cases (e.g. suggestions/validations) and it is costly
  def discoverClasses(definition: ModelDefinition): Set[ClassDefinition] = {
    val componentsAndGlobalVariables =
      definition.components.values ++ definition.expressionConfig.globalVariables.values
    ComponentClassDefinitionDiscovery.discoverClasses(componentsAndGlobalVariables)(definition.settings) ++
      ClassDefinitionDiscovery
        .discoverClassesFromTypes(definition.expressionConfig.additionalClasses.map(Typed(_)))(definition.settings)
  }

}
