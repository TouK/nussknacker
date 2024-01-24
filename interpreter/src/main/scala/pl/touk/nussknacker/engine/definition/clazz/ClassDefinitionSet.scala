package pl.touk.nussknacker.engine.definition.clazz

import pl.touk.nussknacker.engine.api.process.{ClassExtractionSettings, ExpressionConfig}

object ClassDefinitionSet {

  def apply(classDefinitions: Set[ClassDefinition]): ClassDefinitionSet = {
    new ClassDefinitionSet(classDefinitions.toList.map(classDef => classDef.getClazz -> classDef).toMap)
  }

  // Below are for tests purpose mostly
  def forClasses(classes: Class[_]*): ClassDefinitionSet = ClassDefinitionSet(
    ClassDefinitionDiscovery.discoverClasses(classes ++ ExpressionConfig.defaultAdditionalClasses)(
      ClassExtractionSettings.Default
    )
  )

  def forDefaultAdditionalClasses: ClassDefinitionSet = ClassDefinitionSet(
    ClassDefinitionDiscovery.discoverClasses(ExpressionConfig.defaultAdditionalClasses)(ClassExtractionSettings.Default)
  )

}

case class ClassDefinitionSet(classDefinitionsMap: Map[Class[_], ClassDefinition]) {

  def all: Set[ClassDefinition] = classDefinitionsMap.values.toSet

  def get(clazz: Class[_]): Option[ClassDefinition] =
    classDefinitionsMap.get(clazz)

}
