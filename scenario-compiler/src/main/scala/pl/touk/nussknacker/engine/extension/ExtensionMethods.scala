package pl.touk.nussknacker.engine.extension

import pl.touk.nussknacker.engine.definition.clazz.{ClassDefinitionSet, MethodDefinition}
import pl.touk.nussknacker.engine.extension.ExtensionMethods.extensionMethodsHandlers

import java.lang.reflect.{Method, Modifier}

class ExtensionsAwareMethodInvoker(classLoader: ClassLoader, classDefinitionSet: ClassDefinitionSet) {

  private val toInvocationTargetConvertersByClass =
    extensionMethodsHandlers
      .map(e => e.invocationTargetClass -> e.createConverter(classLoader, classDefinitionSet))
      .toMap[Class[_], ToExtensionMethodInvocationTargetConverter[_]]

  def invoke(method: Method)(target: Any, arguments: Array[Object]): Any = {
    if (toInvocationTargetConvertersByClass.contains(method.getDeclaringClass)) {
      toInvocationTargetConvertersByClass
        .get(method.getDeclaringClass)
        .map(_.toInvocationTarget(target))
        .map(impl => method.invoke(impl, arguments: _*))
        .getOrElse {
          throw new IllegalArgumentException(s"Extension method: ${method.getName} is not implemented")
        }
    } else {
      method.invoke(target, arguments: _*)
    }
  }

}

object ExtensionAwareMethodsDiscovery {

  // Calculating methods should not be cached because it's calculated only once at the first execution of
  // parsed expression (org.springframework.expression.spel.ast.MethodReference.getCachedExecutor).
  def discover(clazz: Class[_]): Array[Method] =
    clazz.getMethods ++ extensionMethodsHandlers.filter(_.applies(clazz)).flatMap(_.nonStaticMethods)
}

object ExtensionMethods {

  val extensionMethodsHandlers: List[ExtensionMethodsHandler] = List(
    Cast,
    ArrayExt,
    CollectionConversionExt,
  )

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

  lazy val nonStaticMethods: Array[Method] =
    invocationTargetClass.getDeclaredMethods
      .filter(m => Modifier.isPublic(m.getModifiers) && !Modifier.isStatic(m.getModifiers))

  def createConverter(
      classLoader: ClassLoader,
      set: ClassDefinitionSet
  ): ToExtensionMethodInvocationTargetConverter[ExtensionMethodInvocationTarget]

  def extractDefinitions(clazz: Class[_], set: ClassDefinitionSet): Map[String, List[MethodDefinition]]

  // For what classes is extension available in the runtime invocation
  def applies(clazz: Class[_]): Boolean
}

trait ToExtensionMethodInvocationTargetConverter[ExtensionMethodInvocationTarget] {
  // This method should be as easy and lightweight as possible because it's fired with every method execution.
  def toInvocationTarget(target: Any): ExtensionMethodInvocationTarget
}
