package pl.touk.nussknacker.engine.definition.model

import pl.touk.nussknacker.engine.definition.clazz.ClassDefinitionSet
import pl.touk.nussknacker.engine.extension.ClassDefinitionSetWithExtensionMethods

case class ModelDefinitionWithClasses(modelDefinition: ModelDefinition) {

  @transient lazy val classDefinitions: ClassDefinitionSet = ClassDefinitionSetWithExtensionMethods(
    ClassDefinitionSet(ModelClassDefinitionDiscovery.discoverClasses(modelDefinition))
  ).value

}
