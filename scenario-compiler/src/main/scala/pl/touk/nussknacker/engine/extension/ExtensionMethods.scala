package pl.touk.nussknacker.engine.extension

import org.springframework.core.convert.TypeDescriptor
import org.springframework.expression.{EvaluationContext, MethodExecutor, MethodResolver, TypedValue}
import pl.touk.nussknacker.engine.definition.clazz.{ClassDefinitionSet, MethodDefinition}
import pl.touk.nussknacker.engine.extension.ExtensionMethods.extensionMethodsDefinitions

import java.util
import scala.collection.concurrent.TrieMap

class ExtensionMethodResolver(classDefinitionSet: ClassDefinitionSet) extends MethodResolver {
  private val executorsCache = new TrieMap[(String, Class[_]), Option[MethodExecutor]]()

  override def resolve(
      context: EvaluationContext,
      targetObject: Any,
      methodName: String,
      argumentTypes: util.List[TypeDescriptor]
  ): MethodExecutor =
    maybeResolve(context, targetObject, methodName, argumentTypes).orNull

  def maybeResolve(
      context: EvaluationContext,
      targetObject: Any,
      methodName: String,
      argumentTypes: util.List[TypeDescriptor]
  ): Option[MethodExecutor] = {
    val targetClass = targetObject.getClass
    executorsCache.getOrElse(
      (methodName, targetClass), {
        val maybeExecutor = extensionMethodsDefinitions
          .filter(_.appliesToClassInRuntime(targetClass))
          .flatMap(_.createHandler(classDefinitionSet).findMethod(methodName, argumentTypes.size()))
          .headOption
          .map(createExecutor)
        executorsCache.put((methodName, targetClass), maybeExecutor)
        maybeExecutor
      }
    )
  }

  private def createExecutor(method: ExtensionMethod): MethodExecutor = new MethodExecutor {

    override def execute(context: EvaluationContext, target: Any, args: Object*): TypedValue = {
      new TypedValue(method.invoke(target, args: _*), null)
    }

  }

}

object ExtensionMethods {

  val extensionMethodsDefinitions: List[ExtensionMethodsDefinition] = List(
    CastOrConversionExt,
    ArrayExt,
    ConversionExt(ToLongConversion),
    ConversionExt(ToDoubleConversion),
    ConversionExt(ToBigDecimalConversion),
    ConversionExt(ToBooleanConversion),
    ToListConversionExt,
    ToMapConversionExt,
  )

  def enrichWithExtensionMethods(set: ClassDefinitionSet): ClassDefinitionSet = {
    new ClassDefinitionSet(
      set.classDefinitionsMap.map { case (clazz, definition) =>
        clazz -> definition.copy(
          methods = definition.methods ++ extensionMethodsDefinitions.flatMap(_.extractDefinitions(clazz, set))
        )
      }.toMap // .toMap is needed by scala 2.12
    )
  }

}

trait ExtensionMethod {
  val argsSize: Int
  def invoke(target: Any, args: Object*): Any
}

object ExtensionMethod {

  def NoArg(method: Any => Any): ExtensionMethod = new ExtensionMethod {
    override val argsSize: Int                           = 0
    override def invoke(target: Any, args: Object*): Any = method(target)
  }

  def SingleArg[T](method: (Any, T) => Any): ExtensionMethod = new ExtensionMethod {
    override val argsSize: Int                           = 1
    override def invoke(target: Any, args: Object*): Any = method(target, args.head.asInstanceOf[T])
  }

}

trait ExtensionMethodHandler {
  val methodRegistry: Map[String, ExtensionMethod]

  def findMethod(methodName: String, argsSize: Int): Option[ExtensionMethod] =
    methodRegistry.get(methodName).filter(_.argsSize == argsSize)
}

trait ExtensionMethodsDefinition {
  def createHandler(set: ClassDefinitionSet): ExtensionMethodHandler

  def extractDefinitions(clazz: Class[_], set: ClassDefinitionSet): Map[String, List[MethodDefinition]]

  // For what classes is extension available in the runtime invocation
  def appliesToClassInRuntime(clazz: Class[_]): Boolean
}
