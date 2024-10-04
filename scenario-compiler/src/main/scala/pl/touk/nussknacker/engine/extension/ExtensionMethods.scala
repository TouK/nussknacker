package pl.touk.nussknacker.engine.extension

import pl.touk.nussknacker.engine.definition.clazz.{ClassDefinitionSet, MethodDefinition}
import pl.touk.nussknacker.engine.extension.ExtensionMethods.extensions

import java.lang.reflect.Method

class ExtensionMethodsInvoker(classLoader: ClassLoader, classDefinitionSet: ClassDefinitionSet) {
  private val extensionsByClass = extensions.map(e => e.clazz -> e).toMap[Class[_], Extension]

  def invoke(target: Object, arguments: Array[Object]): PartialFunction[Method, Any] = {
    case method if extensionsByClass.contains(method.getDeclaringClass) =>
      extensionsByClass
        .get(method.getDeclaringClass)
        .map(_.implFactory.create(target, classLoader, classDefinitionSet))
        .map(impl => method.invoke(impl, arguments: _*))
        .getOrElse {
          throw new IllegalArgumentException(s"Extension method: ${method.getName} is not implemented")
        }
  }

}

object ExtensionMethods {

  private[extension] val extensions = List(
    Extension(classOf[Cast], Cast, Cast)
  )

  def enrichWithExtensionMethods(set: ClassDefinitionSet): ClassDefinitionSet = {
    new ClassDefinitionSet(
      set.classDefinitionsMap.map { case (clazz, definition) =>
        clazz -> definition.copy(
          methods = definition.methods ++ extensions.flatMap(_.definitionsExtractor.extractDefinitions(clazz, set))
        )
      }.toMap // .toMap is needed by scala 2.12
    )
  }

}

trait ExtensionMethodsImplFactory {
  def create(target: Any, classLoader: ClassLoader, set: ClassDefinitionSet): Any
}

trait ExtensionMethodsDefinitionsExtractor {
  def extractDefinitions(clazz: Class[_], set: ClassDefinitionSet): Map[String, List[MethodDefinition]]
}

case class Extension(
    clazz: Class[_],
    implFactory: ExtensionMethodsImplFactory,
    definitionsExtractor: ExtensionMethodsDefinitionsExtractor
)
