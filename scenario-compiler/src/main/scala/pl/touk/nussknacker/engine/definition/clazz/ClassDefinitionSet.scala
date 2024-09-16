package pl.touk.nussknacker.engine.definition.clazz

object ClassDefinitionSet {

  def apply(classDefinitions: Set[ClassDefinition]): ClassDefinitionSet = {
    new ClassDefinitionSet(classDefinitions.toList.map(classDef => classDef.getClazz -> classDef).toMap)
  }

}

case class ClassDefinitionSet(classDefinitionsMap: Map[Class[_], ClassDefinition]) {
  val unknown: Option[ClassDefinition] =
    classDefinitionsMap.get(classOf[Any])

  def all: Set[ClassDefinition] = classDefinitionsMap.values.toSet

  def get(clazz: Class[_]): Option[ClassDefinition] =
    classDefinitionsMap.get(clazz)

}
