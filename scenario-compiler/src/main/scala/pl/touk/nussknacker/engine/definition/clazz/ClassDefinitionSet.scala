package pl.touk.nussknacker.engine.definition.clazz

import java.lang.reflect.Method

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

  def isParameterlessMethodAllowed(targetClass: Class[_], method: Method): Boolean = {
    // todo: some memoization
    // todo: should it be validated in runtime for not-dynamic access?
    val classWithParentClasses = extractAllTypes(targetClass)
    classWithParentClasses.exists(hasDirectlyDefinedParameterlessMethod(_, method.getName))
  }

  private def extractAllTypes(clazz: Class[_]): List[Class[_]] = {
    // todo: recursive types (heap safety)?
    val superClassAsList: List[Class[_]] = Option(clazz.getSuperclass).map(List(_)).getOrElse(List.empty)
    val extractedSuperTypes              = (clazz.getInterfaces.toList ++ superClassAsList).flatMap(extractAllTypes)
    clazz :: extractedSuperTypes
  }

  private def hasDirectlyDefinedParameterlessMethod(targetClass: Class[_], methodName: String): Boolean = {
    get(targetClass)
      .flatMap(definition => definition.methods.get(methodName))
      .getOrElse(List.empty)
      .exists { methodDefinition =>
        methodDefinition match {
          case StaticMethodDefinition(signature, _, _) =>
            signature.parametersToList.isEmpty
          case FunctionalMethodDefinition(_, signatures, _, _) =>
            signatures.find(methodInfo => methodInfo.parametersToList.isEmpty).isDefined
        }
      }
  }

}
