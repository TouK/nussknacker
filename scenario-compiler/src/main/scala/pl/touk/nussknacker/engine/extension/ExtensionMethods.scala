package pl.touk.nussknacker.engine.extension

import pl.touk.nussknacker.engine.definition.clazz.ClassDefinitionSet

import java.lang.reflect.Method

object ExtensionMethods {

  private val declarationsWithImplementations = Map[Class[_], ExtensionMethodsImplFactory](
    classOf[Cast] -> CastImpl,
  )

  private val registry: Set[Class[_]] = declarationsWithImplementations.keySet

  def enrichWithExtensionMethods(set: ClassDefinitionSet): ClassDefinitionSet = {
    val castMethodDefinitions = CastMethodDefinitions(set)
    new ClassDefinitionSet(
      set.classDefinitionsMap.map { case (clazz, definition) =>
        clazz -> definition.copy(methods = definition.methods ++ castMethodDefinitions.extractDefinitions(clazz))
      }.toMap // .toMap is needed by scala 2.12
    )
  }

  def invoke(
      method: Method,
      target: Any,
      arguments: Array[Object],
      classLoader: ClassLoader
  ): PartialFunction[Class[_], Any] = {
    case clazz if registry.contains(clazz) =>
      declarationsWithImplementations
        .get(method.getDeclaringClass)
        .map(_.create(target, classLoader))
        .map(impl => method.invoke(impl, arguments: _*))
        .getOrElse {
          throw new IllegalArgumentException(s"Extension method: ${method.getName} is not implemented")
        }
  }

}

trait ExtensionMethodsImplFactory {
  def create(target: Any, classLoader: ClassLoader): Any
}
