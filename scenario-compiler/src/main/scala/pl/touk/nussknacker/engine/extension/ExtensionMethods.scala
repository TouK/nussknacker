package pl.touk.nussknacker.engine.extension

import org.springframework.core.convert.TypeDescriptor
import org.springframework.expression.{EvaluationContext, MethodExecutor, MethodResolver, TypedValue}
import pl.touk.nussknacker.engine.definition.clazz.{ClassDefinitionSet, MethodDefinition}
import pl.touk.nussknacker.engine.extension.ExtensionMethods.extensionMethodsDefinitions

import java.util
import scala.collection.concurrent.TrieMap
import scala.reflect.{classTag, ClassTag}

class ExtensionMethodResolver(classDefinitionSet: ClassDefinitionSet) extends MethodResolver {
  private val executorsCache = new TrieMap[(String, Class[_]), Option[MethodExecutor]]()

  override def resolve(
      context: EvaluationContext,
      targetObject: Any,
      methodName: String,
      argumentTypes: util.List[TypeDescriptor]
  ): MethodExecutor =
    maybeResolve(targetObject, methodName, argumentTypes).orNull

  def maybeResolve(
      targetObject: Any,
      methodName: String,
      argumentTypes: util.List[TypeDescriptor]
  ): Option[MethodExecutor] = {
    val targetClass = targetObject.getClass
    executorsCache.getOrElseUpdate(
      (methodName, targetClass), {
        extensionMethodsDefinitions.flatMap(
          _.findMethod(targetClass, methodName, argumentTypes.size(), classDefinitionSet)
        ) match {
          case Nil           => None
          case method :: Nil => Some(createExecutor(method))
          case _ => throw new IllegalStateException(s"Found too many methods for method with name: '$methodName'")
        }
      }
    )
  }

  private def createExecutor(method: ExtensionMethod[_]): MethodExecutor = new MethodExecutor {

    override def execute(context: EvaluationContext, target: Any, args: Object*): TypedValue = {
      val typeDescriptor = TypeDescriptor.valueOf(method.returnType(args: _*))
      new TypedValue(method.invoke(target, args: _*), typeDescriptor)
    }

  }

}

object ExtensionMethods {

  val extensionMethodsDefinitions: List[ExtensionMethodsDefinition] = List(
    CastOrConversionExt,
    ArrayExt,
    ConversionExt(ToLongConversion),
    ConversionExt(ToIntegerConversion),
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

abstract class ExtensionMethod[R: ClassTag] {
  val argsSize: Int
  def invoke(target: Any, args: Object*): R
  // This method gets the same arguments as passed to invoke
  def returnType(args: Object*): Class[_] = classTag[R].runtimeClass.asInstanceOf[Class[R]]
}

object ExtensionMethod {

  def NoArg[R: ClassTag](method: Any => R): ExtensionMethod[R] =
    new ExtensionMethod[R] {
      override val argsSize: Int                         = 0
      override def invoke(target: Any, args: Object*): R = method(target)
    }

  def NoArg[R: ClassTag](method: Any => R, returnTypeClass: Class[_]): ExtensionMethod[R] =
    new ExtensionMethod[R] {
      override val argsSize: Int                         = 0
      override def invoke(target: Any, args: Object*): R = method(target)
      override def returnType(args: Object*): Class[_]   = returnTypeClass
    }

  def SingleArg[T, R: ClassTag](method: (Any, T) => R): ExtensionMethod[R] =
    new ExtensionMethod[R] {
      override val argsSize: Int                         = 1
      override def invoke(target: Any, args: Object*): R = method(target, args.head.asInstanceOf[T])
    }

  def SingleArg[T, R: ClassTag](method: (Any, T) => R, clazzProvider: T => Class[_]): ExtensionMethod[R] =
    new ExtensionMethod[R] {
      override val argsSize: Int                         = 1
      override def invoke(target: Any, args: Object*): R = method(target, args.head.asInstanceOf[T])
      override def returnType(args: Object*): Class[_]   = clazzProvider(args.head.asInstanceOf[T])
    }

}

trait ExtensionMethodsDefinition {

  def findMethod(
      clazz: Class[_],
      methodName: String,
      argsSize: Int,
      set: ClassDefinitionSet
  ): Option[ExtensionMethod[_]]

  def extractDefinitions(clazz: Class[_], set: ClassDefinitionSet): Map[String, List[MethodDefinition]]

}
