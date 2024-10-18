package pl.touk.nussknacker.engine.extension

import pl.touk.nussknacker.engine.definition.clazz.{ClassDefinitionSet, MethodDefinition}
import pl.touk.nussknacker.engine.extension.ExtensionMethods.extensionMethodsHandlers
import pl.touk.nussknacker.engine.util.classes.Extensions.ClassesExtensions

import java.lang.reflect.Method

class ExtensionMethodsInvoker(classLoader: ClassLoader, classDefinitionSet: ClassDefinitionSet) {

  private val classesBySimpleName = classDefinitionSet.classDefinitionsMap.keySet.classesBySimpleNamesRegardingClashes()

  private val toInvocationTargetConvertersByClass =
    extensionMethodsHandlers
      .map(e => e.invocationTargetClass -> e.createConverter(classLoader, classesBySimpleName))
      .toMap[Class[_], ToExtensionMethodInvocationTargetConverter[_]]

  def invoke(target: Object, arguments: Array[Object]): PartialFunction[Method, Any] = {
    case method if toInvocationTargetConvertersByClass.contains(method.getDeclaringClass) =>
      toInvocationTargetConvertersByClass
        .get(method.getDeclaringClass)
        .map(_.toInvocationTarget(target))
        .map(impl => method.invoke(impl, arguments: _*))
        .getOrElse {
          throw new IllegalArgumentException(s"Extension method: ${method.getName} is not implemented")
        }
  }

}

object ExtensionMethods {

  val extensionMethodsHandlers: List[ExtensionMethodsHandler] = List(Cast, ArrayExt)

  def enrichWithExtensionMethods(set: ClassDefinitionSet): ClassDefinitionSet = {
    new ClassDefinitionSet(
      set.classDefinitionsMap.map { case (clazz, definition) =>
        clazz -> definition.copy(
          methods = definition.methods ++ extensionMethodsHandlers.flatMap(_.extractDefinitions(clazz, set))
        )
      }.toMap // .toMap is needed by scala 2.12
    )
  }

}

trait ExtensionMethodsHandler {

  type ExtensionMethodInvocationTarget

  val invocationTargetClass: Class[ExtensionMethodInvocationTarget]

  def createConverter(
      classLoader: ClassLoader,
      classesBySimpleName: Map[String, Class[_]]
  ): ToExtensionMethodInvocationTargetConverter[ExtensionMethodInvocationTarget]

  def extractDefinitions(clazz: Class[_], set: ClassDefinitionSet): Map[String, List[MethodDefinition]]

  def applies(clazz: Class[_]): Boolean

}

trait ToExtensionMethodInvocationTargetConverter[ExtensionMethodInvocationTarget] {
  // This method should be as easy and lightweight as possible because it's fired with every method execution.
  def toInvocationTarget(target: Any): ExtensionMethodInvocationTarget
}
