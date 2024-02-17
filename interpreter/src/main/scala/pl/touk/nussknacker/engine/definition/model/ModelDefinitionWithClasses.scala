package pl.touk.nussknacker.engine.definition.model

import pl.touk.nussknacker.engine.definition.clazz.ClassDefinitionSet
import pl.touk.nussknacker.engine.definition.component.ComponentWithRuntimeLogicFactory

case class ModelDefinitionWithClasses(modelDefinition: ModelDefinition) {

  @transient lazy val classDefinitions: ClassDefinitionSet = ClassDefinitionSet(
    ModelClassDefinitionDiscovery.discoverClasses(modelDefinition)
  )

}
