package pl.touk.nussknacker.engine.definition.clazz

import com.github.ghik.silencer.silent

object ClassDefinitionSet {

  def apply(classDefinitions: Set[ClassDefinition]): ClassDefinitionSet = {
    new ClassDefinitionSet(classDefinitions.toList.map(classDef => classDef.getClazz -> classDef).toMap)
  }

}

case class ClassDefinitionSet(classDefinitionsMap: Map[Class[_], ClassDefinition]) {
  lazy val unknown: Option[ClassDefinition] = get(classOf[java.lang.Object])

  private lazy val parameterlessMethodsByDeclaringTypes: Map[DeclaringClassWithMethodName, ParameterlessMethodInfo] = {
    def getParameterlessMethods(staticMethods: Boolean) = {
      for {
        (clazz, classDefinition) <- classDefinitionsMap
        (methodName, methods)    <- if (staticMethods) classDefinition.staticMethods else classDefinition.methods
        method                   <- methods
        if method.signatures.exists(_.parametersToList.isEmpty)
      } yield DeclaringClassWithMethodName(clazz, methodName) -> ParameterlessMethodInfo(isStatic = staticMethods)
    }
    getParameterlessMethods(staticMethods = true) ++ getParameterlessMethods(staticMethods = false)
  }

  def all: Set[ClassDefinition] = classDefinitionsMap.values.toSet

  def get(clazz: Class[_]): Option[ClassDefinition] = classDefinitionsMap.get(clazz)

  def isParameterlessMethodAllowed(targetClass: Class[_], methodName: String, staticMethodsOnly: Boolean): Boolean = {
    extractAllTypesInHierarchy(targetClass).exists(typeToCheck =>
      parameterlessMethodsByDeclaringTypes
        .get(DeclaringClassWithMethodName(typeToCheck, methodName))
        .exists(methodInfo => !staticMethodsOnly || methodInfo.isStatic)
    )
  }

  // We do runtime "types-walking" during expression invocation because in ClassDefinitionSet
  // we have some definitions for interface (e.g. List, Map.Entry) but we don't know exact used
  // implementation (like ArrayList or Collections.UnmodifiableMap.UnmodifiableEntrySet.UnmodifiableEntry) so we can't precompute it easily
  @silent("deprecated")
  private def extractAllTypesInHierarchy(clazz: Class[_]): Stream[Class[_]] = {
    lazy val superClass          = Option(clazz.getSuperclass).map(_ #:: Stream.empty).getOrElse(Stream.empty)
    lazy val extractedSuperTypes = (superClass #::: clazz.getInterfaces.toStream).flatMap(extractAllTypesInHierarchy)
    clazz #:: extractedSuperTypes
  }

}

private final case class ParameterlessMethodInfo(isStatic: Boolean)

private final case class DeclaringClassWithMethodName(clazz: Class[_], methodName: String)
