package pl.touk.nussknacker.engine.definition.clazz

object ClassDefinitionSet {

  def apply(classDefinitions: Set[ClassDefinition]): ClassDefinitionSet = {
    new ClassDefinitionSet(classDefinitions.toList.map(classDef => classDef.getClazz -> classDef).toMap)
  }

}

case class ClassDefinitionSet(classDefinitionsMap: Map[Class[_], ClassDefinition]) {
  lazy val unknown = get(classOf[java.lang.Object])

  def all: Set[ClassDefinition] = classDefinitionsMap.values.toSet

  def get(clazz: Class[_]): Option[ClassDefinition] =
    classDefinitionsMap.get(clazz)

  def isParameterlessMethodAllowed(targetClass: Class[_], method: String): Boolean = {
    // todo: some memoization
    val classWithParentClasses = extractAllTypes(targetClass)
    classWithParentClasses.exists(hasDirectlyDefinedParameterlessMethod(_, method))
  }

  private def extractAllTypes(clazz: Class[_]): List[Class[_]] = {
    val superClassAsList: List[Class[_]] = Option(clazz.getSuperclass).map(List(_)).getOrElse(List.empty)
    val extractedSuperTypes              = (clazz.getInterfaces.toList ++ superClassAsList).flatMap(extractAllTypes)
    clazz :: extractedSuperTypes
  }

  private def hasDirectlyDefinedParameterlessMethod(targetClass: Class[_], methodName: String): Boolean = {
    get(targetClass)
      .map(definition =>
        definition.methods.getOrElse(methodName, List.empty) ++ definition.staticMethods
          .getOrElse(methodName, List.empty)
      ) // todo: in some contexts should we force static only methods (in case of dealing with class type in variable)???
      .getOrElse(List.empty)
      .exists(_.signatures.find(_.parametersToList.isEmpty).isDefined)
  }

}
