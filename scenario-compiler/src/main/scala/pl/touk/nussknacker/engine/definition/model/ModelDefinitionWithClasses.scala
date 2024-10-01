package pl.touk.nussknacker.engine.definition.model

import pl.touk.nussknacker.engine.definition.clazz.ClassDefinitionSet
import pl.touk.nussknacker.engine.extension.ExtensionMethods

case class ModelDefinitionWithClasses(modelDefinition: ModelDefinition) {

  @transient lazy val classDefinitions: ClassDefinitionSet = ExtensionMethods.enrichWithExtensionMethods(
    ClassDefinitionSet(ModelClassDefinitionDiscovery.discoverClasses(modelDefinition))
  )

}
