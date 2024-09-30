package pl.touk.nussknacker.engine.extension

import cats.data.ValidatedNel
import cats.implicits.catsSyntaxValidatedId
import pl.touk.nussknacker.engine.api.generics.{GenericFunctionTypingError, MethodTypeInfo, Parameter}
import pl.touk.nussknacker.engine.api.typed.typing
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedObjectWithValue, TypingResult, Unknown}
import pl.touk.nussknacker.engine.definition.clazz.{ClassDefinitionSet, FunctionalMethodDefinition, MethodDefinition}
import pl.touk.nussknacker.engine.extension.CastMethodDefinitions._
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap

import scala.util.Try

sealed trait Cast {
  def canCastTo(className: String): Boolean
  def castTo[T](className: String): T
  def castToOrNull[T >: Null](className: String): T
}

object Cast {
  private[extension] val canCastToMethodName    = "canCastTo"
  private[extension] val castToMethodName       = "castTo"
  private[extension] val castToOrNullMethodName = "castToOrNull"

  private val castMethodsNames = Set(
    canCastToMethodName,
    castToMethodName,
    castToOrNullMethodName,
  )

  def isCastMethod(methodName: String): Boolean =
    castMethodsNames.contains(methodName)
}

class CastImpl(target: Any, classLoader: ClassLoader) extends Cast {

  override def canCastTo(className: String): Boolean =
    classLoader.loadClass(className).isAssignableFrom(target.getClass)

  override def castTo[T](className: String): T = {
    val clazz = classLoader.loadClass(className)
    if (clazz.isInstance(target)) {
      clazz.cast(target).asInstanceOf[T]
    } else {
      throw new ClassCastException(s"Cannot cast: ${target.getClass} to: $className")
    }
  }

  override def castToOrNull[T >: Null](className: String): T = Try { castTo[T](className) }.getOrElse(null)
}

private[extension] object CastImpl extends ExtensionMethodsImplFactory {
  override def create(target: Any, classLoader: ClassLoader): Any =
    new CastImpl(target, classLoader)
}

private[extension] class CastMethodDefinitions(private val classesWithTyping: Map[Class[_], TypingResult]) {

  def createDefinitions(clazz: Class[_]): Map[String, List[MethodDefinition]] =
    classesWithTyping.filterKeysNow(c => isAssignable(clazz, c)) match {
      case allowedClasses if allowedClasses.isEmpty => Map.empty
      case allowedClasses                           => definitions(allowedClasses)
    }

  private def isAssignable(clazz: Class[_], targetClazz: Class[_]): Boolean =
    clazz != targetClazz &&
      clazz.isAssignableFrom(targetClazz)

  private def definitions(allowedClasses: Map[Class[_], TypingResult]): Map[String, List[MethodDefinition]] =
    List(
      FunctionalMethodDefinition(
        (_, x) => canCastToTyping(allowedClasses)(x),
        methodTypeInfoWithStringParam,
        Cast.canCastToMethodName,
        Some("Checks if a type can be casted to a given class")
      ),
      FunctionalMethodDefinition(
        (_, x) => castToTyping(allowedClasses)(x),
        methodTypeInfoWithStringParam,
        Cast.castToMethodName,
        Some("Casts a type to a given class or throws exception if type cannot be casted.")
      ),
      FunctionalMethodDefinition(
        (_, x) => castToTyping(allowedClasses)(x),
        methodTypeInfoWithStringParam,
        Cast.castToOrNullMethodName,
        Some("Casts a type to a given class or return null if type cannot be casted.")
      ),
    ).groupBy(_.name)

  private def castToTyping(allowedClasses: Map[Class[_], TypingResult])(
      arguments: List[typing.TypingResult]
  ): ValidatedNel[GenericFunctionTypingError, typing.TypingResult] = arguments match {
    case TypedObjectWithValue(_, clazzName: String) :: Nil =>
      allowedClasses.find(_._1.getName == clazzName).map(_._2) match {
        case Some(typing) => typing.validNel
        case None         => GenericFunctionTypingError.OtherError(s"Casting to '$clazzName' is not allowed").invalidNel
      }
    case _ => GenericFunctionTypingError.ArgumentTypeError.invalidNel
  }

  private def canCastToTyping(allowedClasses: Map[Class[_], TypingResult])(
      arguments: List[typing.TypingResult]
  ): ValidatedNel[GenericFunctionTypingError, typing.TypingResult] =
    castToTyping(allowedClasses)(arguments).map(_ => Typed.typedClass[Boolean])

}

object CastMethodDefinitions {
  private val stringClass = classOf[String]

  private val methodTypeInfoWithStringParam = MethodTypeInfo(
    noVarArgs = List(
      Parameter("className", Typed.genericTypeClass(stringClass, Nil))
    ),
    varArg = None,
    result = Unknown
  )

  def apply(set: ClassDefinitionSet): CastMethodDefinitions =
    new CastMethodDefinitions(
      set.classDefinitionsMap
        .map { case (clazz, classDefinition) =>
          clazz -> Try(classDefinition.clazzName).toOption
        }
        .collect { case (clazz: Class[_], Some(t)) =>
          clazz -> t
        }
        .toMap
    )

}
