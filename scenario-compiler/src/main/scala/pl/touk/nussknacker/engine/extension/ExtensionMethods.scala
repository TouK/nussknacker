package pl.touk.nussknacker.engine.extension

import pl.touk.nussknacker.engine.definition.clazz.{ClassDefinitionSet, MethodDefinition}
import pl.touk.nussknacker.engine.extension.ExtensionMethods.extensionMethodsHandlers

import java.lang.reflect.{InvocationTargetException, Method, Modifier}
import scala.util.{Failure, Try}

class ExtensionsAwareMethodInvoker(classDefinitionSet: ClassDefinitionSet) {

  private val toInvocationTargetConvertersByClass =
    extensionMethodsHandlers
      .map(e => e.invocationTargetClass -> e.createConverter(classDefinitionSet))
      .toMap[Class[_], ToExtensionMethodInvocationTargetConverter[_ <: ExtensionMethodInvocationTarget]]

  def invoke(method: Method)(target: Any, arguments: Array[Object]): Any = {
    if (toInvocationTargetConvertersByClass.contains(method.getDeclaringClass)) {
      toInvocationTargetConvertersByClass
        .get(method.getDeclaringClass)
        .map(_.toInvocationTarget(target))
        .map(impl => invokeMethodWithoutReflection(method, arguments, impl))
        .getOrElse {
          throw new IllegalArgumentException(s"Extension method: ${method.getName} is not implemented")
        }
    } else {
      method.invoke(target, arguments: _*)
    }
  }

  private def invokeMethodWithoutReflection(
      method: Method,
      arguments: Array[Object],
      impl: ExtensionMethodInvocationTarget
  ): Any = {
    Try(impl.invoke(method.getName, arguments)).recoverWith { case ex: Throwable =>
      Failure(new InvocationTargetException(ex))
    }.get
  }

}

object ExtensionAwareMethodsDiscovery {

  // Calculating methods should not be cached because it's calculated only once at the first execution of
  // parsed expression (org.springframework.expression.spel.ast.MethodReference.getCachedExecutor).
  def discover(clazz: Class[_]): Array[Method] =
    clazz.getMethods ++ extensionMethodsHandlers.filter(_.appliesToClassInRuntime(clazz)).flatMap(_.nonStaticMethods)
}

object ExtensionMethods {

  val extensionMethodsHandlers: List[ExtensionMethodsHandler[_ <: ExtensionMethodInvocationTarget]] = List(
    CastOrConversionExt,
    ArrayExt,
    ToLongConversionExt,
    ToDoubleConversionExt,
    ToBigDecimalConversionExt,
    ToBooleanConversionExt,
    ToListConversionExt,
    ToMapConversionExt,
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

trait ExtensionMethodInvocationTarget {
  def invoke(methodName: String, arguments: Array[Object]): Any
}

trait ExtensionMethodsHandler[T <: ExtensionMethodInvocationTarget] {
  val invocationTargetClass: Class[T]

  lazy val nonStaticMethods: Array[Method] =
    invocationTargetClass.getDeclaredMethods
      .filter(m => Modifier.isPublic(m.getModifiers) && !Modifier.isStatic(m.getModifiers))

  def createConverter(
      set: ClassDefinitionSet
  ): ToExtensionMethodInvocationTargetConverter[T]

  def extractDefinitions(clazz: Class[_], set: ClassDefinitionSet): Map[String, List[MethodDefinition]]

  // For what classes is extension available in the runtime invocation
  def appliesToClassInRuntime(clazz: Class[_]): Boolean
}

trait ToExtensionMethodInvocationTargetConverter[T <: ExtensionMethodInvocationTarget] {
  // This method should be as easy and lightweight as possible because it's fired with every method execution.
  def toInvocationTarget(target: Any): T
}
