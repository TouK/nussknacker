package pl.touk.nussknacker.engine.extension

import pl.touk.nussknacker.engine.definition.clazz.ClassDefinitionSet

import java.lang.reflect.Method

class ExtensionMethods(classLoader: ClassLoader, classDefinitionSet: ClassDefinitionSet) {

  private val declarationsWithImplementations = Map[Class[_], ExtensionMethodsImplFactory](
    classOf[Cast] -> CastImplFactory(classLoader, classDefinitionSet),
  )

  def invoke(method: Method, target: Object, arguments: Array[Object]): PartialFunction[Class[_], Any] = {
    case clazz if declarationsWithImplementations.contains(clazz) =>
      declarationsWithImplementations
        .get(method.getDeclaringClass)
        .map(_.create(target))
        .map(impl => method.invoke(impl, arguments: _*))
        .getOrElse {
          throw new IllegalArgumentException(s"Extension method: ${method.getName} is not implemented")
        }
  }

}

object ExtensionMethods {

  def enrichWithExtensionMethods(set: ClassDefinitionSet): ClassDefinitionSet = {
    val castMethodDefinitions = new CastMethodDefinitions(set)
    new ClassDefinitionSet(
      set.classDefinitionsMap.map { case (clazz, definition) =>
        clazz -> definition.copy(methods = definition.methods ++ castMethodDefinitions.extractDefinitions(clazz))
      }.toMap // .toMap is needed by scala 2.12
    )
  }

}

trait ExtensionMethodsImplFactory {
  def create(target: Any): Any
}
