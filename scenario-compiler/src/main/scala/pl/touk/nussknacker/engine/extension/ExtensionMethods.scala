package pl.touk.nussknacker.engine.extension

import pl.touk.nussknacker.engine.definition.clazz.{ClassDefinitionSet, MethodDefinition}
import pl.touk.nussknacker.engine.extension.ExtensionMethods.extensions
import pl.touk.nussknacker.engine.util.classes.Extensions.ClassesExtensions

import java.lang.reflect.Method

class ExtensionMethodsInvoker(classLoader: ClassLoader, classDefinitionSet: ClassDefinitionSet) {
  private val extensionsByClass   = extensions.map(e => e.clazz -> e).toMap[Class[_], Extension]
  private val classesBySimpleName = classDefinitionSet.classDefinitionsMap.keySet.classesBySimpleNamesRegardingClashes()

  def invoke(target: Object, arguments: Array[Object]): PartialFunction[Method, Any] = {
    case method if extensionsByClass.contains(method.getDeclaringClass) =>
      extensionsByClass
        .get(method.getDeclaringClass)
        .map(_.implFactory.create(target, classLoader, classesBySimpleName))
        .map(impl => method.invoke(impl, arguments: _*))
        .getOrElse {
          throw new IllegalArgumentException(s"Extension method: ${method.getName} is not implemented")
        }
  }

}

object ExtensionMethods {

  val extensions = List(
    new Extension(classOf[Cast], Cast, Cast, Cast),
    new Extension(classOf[ArrayExt], ArrayExt, ArrayExt, ArrayExt),
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

trait ExtensionMethodsFactory {
  // Factory method should be as easy and lightweight as possible because it's fired with every method execution.
  def create(target: Any, classLoader: ClassLoader, classesBySimpleName: Map[String, Class[_]]): Any
}

trait ExtensionMethodsDefinitionsExtractor {
  def extractDefinitions(clazz: Class[_], set: ClassDefinitionSet): Map[String, List[MethodDefinition]]
}

// For what classes is extension available in the runtime invocation
trait ExtensionRuntimeApplicable {
  def applies(clazz: Class[_]): Boolean
}

class Extension(
    val clazz: Class[_],
    val implFactory: ExtensionMethodsFactory,
    val definitionsExtractor: ExtensionMethodsDefinitionsExtractor,
    val runtimeApplicable: ExtensionRuntimeApplicable
)
