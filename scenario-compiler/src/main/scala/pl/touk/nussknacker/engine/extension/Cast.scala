package pl.touk.nussknacker.engine.extension

import cats.data.ValidatedNel
import cats.implicits.catsSyntaxValidatedId
import pl.touk.nussknacker.engine.api.generics.{GenericFunctionTypingError, MethodTypeInfo, Parameter}
import pl.touk.nussknacker.engine.api.typed.typing
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedObjectWithValue, TypingResult, Unknown}
import pl.touk.nussknacker.engine.definition.clazz.{ClassDefinitionSet, FunctionalMethodDefinition, MethodDefinition}
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap
import pl.touk.nussknacker.engine.util.classes.Extensions.{ClassExtensions, ClassesExtensions}

import scala.util.Try

sealed trait Cast {
  def canCastTo(className: String): Boolean
  def castTo[T](className: String): T
  def castToOrNull[T >: Null](className: String): T
}

object Cast extends ExtensionMethodsImplFactory with ExtensionMethodsDefinitionsExtractor {
  private val canCastToMethodName    = "canCastTo"
  private val castToMethodName       = "castTo"
  private val castToOrNullMethodName = "castToOrNull"
  private val stringClass            = classOf[String]

  private val methodTypeInfoWithStringParam = MethodTypeInfo(
    noVarArgs = List(
      Parameter("className", Typed.genericTypeClass(stringClass, Nil))
    ),
    varArg = None,
    result = Unknown
  )

  private val castMethodsNames = Set(
    canCastToMethodName,
    castToMethodName,
    castToOrNullMethodName,
  )

  def isCastMethod(methodName: String): Boolean =
    castMethodsNames.contains(methodName)

  override def create(target: Any, classLoader: ClassLoader, set: ClassDefinitionSet): Any =
    new CastImpl(target, classLoader, set.classDefinitionsMap.keySet.classesBySimpleNames())

  override def extractDefinitions(clazz: Class[_], set: ClassDefinitionSet): Map[String, List[MethodDefinition]] = clazz
    .findAllowedClassesForCastParameter(set)
    .mapValuesNow(_.clazzName) match {
    case allowedClasses if allowedClasses.isEmpty => Map.empty
    case allowedClasses                           => definitions(allowedClasses)
  }

  private def definitions(allowedClasses: Map[Class[_], TypingResult]): Map[String, List[MethodDefinition]] =
    List(
      FunctionalMethodDefinition(
        (_, x) => canCastToTyping(allowedClasses)(x),
        methodTypeInfoWithStringParam,
        Cast.canCastToMethodName,
        Some("Checks if a type can be cast to a given class")
      ),
      FunctionalMethodDefinition(
        (_, x) => castToTyping(allowedClasses)(x),
        methodTypeInfoWithStringParam,
        Cast.castToMethodName,
        Some("Casts a type to a given class or throws exception if type cannot be cast.")
      ),
      FunctionalMethodDefinition(
        (_, x) => castToTyping(allowedClasses)(x),
        methodTypeInfoWithStringParam,
        Cast.castToOrNullMethodName,
        Some("Casts a type to a given class or return null if type cannot be cast.")
      ),
    ).groupBy(_.name)

  private def castToTyping(allowedClasses: Map[Class[_], TypingResult])(
      arguments: List[typing.TypingResult]
  ): ValidatedNel[GenericFunctionTypingError, typing.TypingResult] = arguments match {
    case TypedObjectWithValue(_, clazzName: String) :: Nil =>
      allowedClasses
        .find(_._1.equalsScalaClassNameIgnoringCase(clazzName))
        .map(_._2) match {
        case Some(typing) => typing.validNel
        case None =>
          GenericFunctionTypingError.OtherError(s"Casting to '$clazzName' is not allowed").invalidNel
      }
    case _ => GenericFunctionTypingError.ArgumentTypeError.invalidNel
  }

  private def canCastToTyping(allowedClasses: Map[Class[_], TypingResult])(
      arguments: List[typing.TypingResult]
  ): ValidatedNel[GenericFunctionTypingError, typing.TypingResult] =
    castToTyping(allowedClasses)(arguments).map(_ => Typed.typedClass[Boolean])

}

class CastImpl(target: Any, classLoader: ClassLoader, classesBySimpleName: Map[String, Class[_]]) extends Cast {

  override def canCastTo(className: String): Boolean =
    getClass(className) match {
      case Some(clazz) => clazz.isAssignableFrom(target.getClass)
      case None        => false
    }

  override def castTo[T](className: String): T = castToEither[T](className) match {
    case Left(ex)     => throw ex
    case Right(value) => value
  }

  override def castToOrNull[T >: Null](className: String): T = castToEither[T](className) match {
    case Right(value) => value
    case _            => null
  }

  private def castToEither[T](className: String): Either[Throwable, T] =
    getClass(className) match {
      case Some(clazz) if clazz.isInstance(target) => Try(clazz.cast(target).asInstanceOf[T]).toEither
      case _ => Left(new ClassCastException(s"Cannot cast: ${target.getClass} to: $className"))
    }

  private def getClass(name: String): Option[Class[_]] = classesBySimpleName.get(name) match {
    case Some(clazz) => Some(clazz)
    case None        => Try(classLoader.loadClass(name)).toOption
  }

}
