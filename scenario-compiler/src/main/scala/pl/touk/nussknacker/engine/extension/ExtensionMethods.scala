package pl.touk.nussknacker.engine.extension

import org.springframework.core.MethodParameter
import org.springframework.core.convert.TypeDescriptor
import org.springframework.expression.{EvaluationContext, MethodExecutor, MethodResolver, TypedValue}
import pl.touk.nussknacker.engine.definition.clazz.{ClassDefinitionSet, MethodDefinition}
import pl.touk.nussknacker.engine.extension.ExtensionMethods.extensionMethodsHandlers

import java.lang.reflect.{Method, Modifier}
import java.util
import scala.collection.concurrent.TrieMap

class ExtensionMethodResolver(classDefinitionSet: ClassDefinitionSet) extends MethodResolver {
  private type TargetConverter = ToExtensionMethodInvocationTargetConverter[_ <: ExtensionMethodInvocationTarget]

  private val toInvocationTargetConvertersByHandler = extensionMethodsHandlers
    .map(e => e -> e.createConverter(classDefinitionSet))
    .toMap[ExtensionMethodsHandler[_], TargetConverter]

  private val executorsCache = new TrieMap[(String, Class[_]), Option[MethodExecutor]]()

  override def resolve(
      context: EvaluationContext,
      targetObject: Any,
      methodName: String,
      argumentTypes: util.List[TypeDescriptor]
  ): MethodExecutor = {
    maybeResolve(context, targetObject, methodName, argumentTypes).orNull
  }

  def maybeResolve(
      context: EvaluationContext,
      targetObject: Any,
      methodName: String,
      argumentTypes: util.List[TypeDescriptor]
  ): Option[MethodExecutor] = {
    val targetClass = targetObject.getClass
    executorsCache
      .getOrElse(
        (methodName, targetClass), {
          val maybeExecutor = methodByConverter(targetClass, methodName, argumentTypes).map {
            case (method, converter) => createExecutor(method, converter)
          }
          executorsCache.put((methodName, targetClass), maybeExecutor)
          maybeExecutor
        }
      )
  }

  private def methodByConverter(
      targetClass: Class[_],
      methodName: String,
      argumentTypes: util.List[TypeDescriptor]
  ): Option[(Method, TargetConverter)] = {
    toInvocationTargetConvertersByHandler
      .filter(_._1.appliesToClassInRuntime(targetClass))
      .flatMap { case (handler, converter) =>
        handler
          .findMethod(methodName, argumentTypes.size())
          .map(method => method -> converter)
      }
      .headOption
  }

  private def createExecutor(method: Method, converter: TargetConverter): MethodExecutor =
    new MethodExecutor {

      override def execute(context: EvaluationContext, target: Any, arguments: Object*): TypedValue = {
        val value = converter.toInvocationTarget(target).invoke(method.getName, arguments.toArray)
        new TypedValue(value, new TypeDescriptor(new MethodParameter(method, -1)).narrow(value))
      }

    }

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

  private lazy val nonStaticMethods: Array[Method] =
    invocationTargetClass.getDeclaredMethods
      .filter(m => Modifier.isPublic(m.getModifiers) && !Modifier.isStatic(m.getModifiers))

  def findMethod(methodName: String, argsSize: Int): Option[Method] =
    nonStaticMethods.find(m => m.getName.equals(methodName) && m.getParameterCount == argsSize)

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
