package pl.touk.nussknacker.engine.definition.clazz

import pl.touk.nussknacker.engine.api.process.ExpressionConfig

object ClassDefinitionSet {

  def apply(classDefinitions: Set[ClassDefinition]): ClassDefinitionSet = {
    new ClassDefinitionSet(classDefinitions.toList.map(classDef => classDef.getClazz -> classDef).toMap)
  }

  // Below are for tests purpose mostly
  def forClasses(classes: Class[_]*): ClassDefinitionSet = ClassDefinitionSet(
    ClassDefinitionDiscovery.Default.discoverClasses(classes ++ ExpressionConfig.defaultAdditionalClasses)
  )

  def forDefaultAdditionalClasses: ClassDefinitionSet = ClassDefinitionSet(
    ClassDefinitionDiscovery.Default.discoverClasses(ExpressionConfig.defaultAdditionalClasses)
  )

}

case class ClassDefinitionSet(classDefinitionsMap: Map[Class[_], ClassDefinition]) {
  val unknown: Option[ClassDefinition] =
    classDefinitionsMap.get(classOf[Any])

  def all: Set[ClassDefinition] = classDefinitionsMap.values.toSet

  def get(clazz: Class[_]): Option[ClassDefinition] =
    classDefinitionsMap.get(clazz)

}
