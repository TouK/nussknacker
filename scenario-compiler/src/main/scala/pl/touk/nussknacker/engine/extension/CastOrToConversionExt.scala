package pl.touk.nussknacker.engine.extension

import cats.data.ValidatedNel
import cats.implicits.catsSyntaxValidatedId
import pl.touk.nussknacker.engine.api.generics.{GenericFunctionTypingError, MethodTypeInfo, Parameter}
import pl.touk.nussknacker.engine.api.typed.typing
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedObjectWithValue, TypingResult, Unknown}
import pl.touk.nussknacker.engine.definition.clazz.{ClassDefinitionSet, FunctionalMethodDefinition, MethodDefinition}
import pl.touk.nussknacker.engine.extension.CastOrToConversionExt.getConversion
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap
import pl.touk.nussknacker.engine.util.classes.Extensions.{ClassExtensions, ClassesExtensions}

import java.lang.{Boolean => JBoolean}
import scala.util.Try

// todo: lbg - add casting methods to UTIL
class CastOrToConversionExt(target: Any, classesBySimpleName: Map[String, Class[_]]) {

  def is(className: String): Boolean =
    getClass(className).exists(clazz => clazz.isAssignableFrom(target.getClass)) ||
      getConversion(className).exists(_.canConvert(target))

  def to(className: String): Any = tryCast(className) match {
    case Right(value) => value
    case Left(ex1) =>
      tryConvert(className) match {
        case Right(value) => value
        case Left(ex2) =>
          val exception = new IllegalStateException(s"Cannot cast or convert value: $target to: '$className'")
          exception.addSuppressed(ex1)
          exception.addSuppressed(ex2)
          throw exception
      }
  }

  def toOrNull(className: String): Any = tryCast(className) match {
    case Right(value) => value
    case Left(_) =>
      tryConvert(className) match {
        case Right(value) => value
        case Left(_)      => null
      }
  }

  private def tryCast(className: String): Either[Throwable, Any] = getClass(className) match {
    case Some(clazz) => Try(clazz.cast(target)).toEither
    case None        => Left(new ClassCastException(s"Cannot cast: [$target] to: [$className]."))
  }

  private def getClass(className: String): Option[Class[_]] =
    classesBySimpleName.get(className.toLowerCase())

  private def tryConvert(className: String): Either[Throwable, Any] =
    getConversion(className)
      .flatMap(_.convertEither(target))

}

object CastOrToConversionExt extends ExtensionMethodsHandler {
  private val isMethodName              = "is"
  private val toMethodName              = "to"
  private val toOrNullMethodName        = "toOrNull"
  private val castOrToConversionMethods = Set(isMethodName, toMethodName, toOrNullMethodName)
  private val stringClass               = classOf[String]

  private val conversionsRegistry: List[Conversion] = List(
    ToLongConversion,
    ToDoubleConversion,
    ToBigDecimalConversion,
    ToBooleanConversion,
    ToStringConversion,
    ToMapConversion,
    ToListConversion,
  )

  private val conversionsByType: Map[String, Conversion] = conversionsRegistry
    .flatMap(c => c.resultTypeClass.classByNameAndSimpleNameLowerCase().map(n => n._1 -> c))
    .toMap

  override type ExtensionMethodInvocationTarget = CastOrToConversionExt
  override val invocationTargetClass: Class[CastOrToConversionExt] = classOf[CastOrToConversionExt]

  def isCastOrToConversionMethod(methodName: String): Boolean =
    castOrToConversionMethods.contains(methodName)

  def allowedConversions(clazz: Class[_]): List[Conversion] = conversionsRegistry.filter(_.applies(clazz))

  override def createConverter(
      set: ClassDefinitionSet
  ): ToExtensionMethodInvocationTargetConverter[CastOrToConversionExt] = {
    val classesBySimpleName = set.classDefinitionsMap.keySet.classesByNamesAndSimpleNamesLowerCase()
    (target: Any) => new CastOrToConversionExt(target, classesBySimpleName)
  }

  override def extractDefinitions(clazz: Class[_], set: ClassDefinitionSet): Map[String, List[MethodDefinition]] = {
    val castAllowedClasses = clazz.findAllowedClassesForCastParameter(set).mapValuesNow(_.clazzName)
    val isConvertibleClass = conversionsRegistry.exists(_.applies(clazz))
    if (castAllowedClasses.nonEmpty || isConvertibleClass) {
      definitions(castAllowedClasses)
    } else {
      Map.empty
    }
  }

  // Convert methods should visible in runtime for every class because we allow invoke convert methods on an unknown
  // object in Typer, but in the runtime the same type could be known and that's why should add convert method for an
  // every class.
  override def applies(clazz: Class[_]): Boolean = true

  private def getConversion(className: String): Either[Throwable, Conversion] =
    conversionsByType.get(className.toLowerCase) match {
      case Some(conversion) => Right(conversion)
      case None             => Left(new IllegalArgumentException(s"Conversion for class $className not found"))
    }

  private def definitions(allowedClasses: Map[Class[_], TypingResult]): Map[String, List[MethodDefinition]] =
    List(
      FunctionalMethodDefinition(
        (_, x) => canConvertToTyping(allowedClasses)(x),
        methodTypeInfoWithStringParam(Typed.typedClass[JBoolean]),
        "is",
        Some("Checks if a type can be converted to a given class")
      ),
      FunctionalMethodDefinition(
        (_, x) => convertToTyping(allowedClasses)(x),
        methodTypeInfoWithStringParam(Unknown),
        "to",
        Some("Converts a type to a given class or throws exception if type cannot be converted.")
      ),
      FunctionalMethodDefinition(
        (_, x) => convertToTyping(allowedClasses)(x),
        methodTypeInfoWithStringParam(Unknown),
        "toOrNull",
        Some("Converts a type to a given class or return null if type cannot be converted.")
      ),
    ).groupBy(_.name)

  private def convertToTyping(allowedClasses: Map[Class[_], TypingResult])(
      arguments: List[typing.TypingResult]
  ): ValidatedNel[GenericFunctionTypingError, typing.TypingResult] = arguments match {
    case TypedObjectWithValue(_, clazzName: String) :: Nil =>
      allowedClasses
        .find(_._1.equalsScalaClassNameIgnoringCase(clazzName))
        .map(_._2)
        .orElse(getConversion(clazzName).map(_.typingResult).toOption) match {
        case Some(result) => result.validNel
        case None => GenericFunctionTypingError.OtherError(s"Cannot cast or convert to: '$clazzName'").invalidNel
      }
    case _ => GenericFunctionTypingError.ArgumentTypeError.invalidNel
  }

  private def canConvertToTyping(allowedClasses: Map[Class[_], TypingResult])(
      arguments: List[typing.TypingResult]
  ): ValidatedNel[GenericFunctionTypingError, typing.TypingResult] =
    convertToTyping(allowedClasses)(arguments).map(_ => Typed.typedClass[Boolean])

  private def methodTypeInfoWithStringParam(result: TypingResult) = MethodTypeInfo(
    noVarArgs = List(
      Parameter("className", Typed.genericTypeClass(stringClass, Nil))
    ),
    varArg = None,
    result = result
  )

}
