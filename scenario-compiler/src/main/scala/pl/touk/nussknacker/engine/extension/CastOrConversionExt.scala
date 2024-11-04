package pl.touk.nussknacker.engine.extension

import cats.data.ValidatedNel
import cats.implicits.catsSyntaxValidatedId
import com.typesafe.scalalogging.LazyLogging
import org.apache.commons.lang3.LocaleUtils
import org.springframework.util.StringUtils
import pl.touk.nussknacker.engine.api.generics.{GenericFunctionTypingError, MethodTypeInfo, Parameter}
import pl.touk.nussknacker.engine.api.typed.typing
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedObjectWithValue, TypingResult, Unknown}
import pl.touk.nussknacker.engine.definition.clazz.{ClassDefinitionSet, FunctionalMethodDefinition, MethodDefinition}
import pl.touk.nussknacker.engine.extension.CastOrConversionExt.getConversion
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap
import pl.touk.nussknacker.engine.util.classes.Extensions.{ClassExtensions, ClassesExtensions}

import java.lang.{Boolean => JBoolean}
import java.nio.charset.Charset
import java.time.chrono.{ChronoLocalDate, ChronoLocalDateTime}
import java.time.{LocalDate, LocalDateTime, LocalTime, ZoneId, ZoneOffset}
import java.util.{Currency, UUID}
import scala.util.Try

// todo: lbg - add casting methods to UTIL
class CastOrConversionExt(target: Any, classesBySimpleName: Map[String, Class[_]]) extends LazyLogging {

  def is(className: String): Boolean =
    getClass(className).exists(clazz => clazz.isAssignableFrom(target.getClass)) ||
      getConversion(className).exists(_.canConvert(target))

  def to(className: String): Any =
    orElse(tryCast(className), tryConvert(className)) match {
      case Right(value) => value
      case Left(ex) => throw new IllegalStateException(s"Cannot cast or convert value: $target to: '$className'", ex)
    }

  def toOrNull(className: String): Any =
    orElse(tryCast(className), tryConvert(className))
      .getOrElse(null)

  private def tryCast(className: String): Either[Throwable, Any] = getClass(className) match {
    case Some(clazz) => Try(clazz.cast(target)).toEither
    case None        => Left(new ClassCastException(s"Cannot cast: [$target] to: [$className]."))
  }

  private def getClass(className: String): Option[Class[_]] =
    classesBySimpleName.get(className.toLowerCase())

  private def tryConvert(className: String): Either[Throwable, Any] =
    getConversion(className).flatMap(_.convertEither(target)) match {
      case r @ Right(_) => r
      case l @ Left(ex) =>
        logger.debug(s"Conversion from value: $target to '$className' failed", ex)
        l
    }

  // scala 2.12 does not support either.orElse
  private def orElse(e1: Either[Throwable, Any], e2: => Either[Throwable, Any]): Either[Throwable, Any] =
    e1 match {
      case Left(_)      => e2
      case r @ Right(_) => r
    }

}

object CastOrConversionExt extends ExtensionMethodsHandler {
  private val isMethodName            = "is"
  private val toMethodName            = "to"
  private val toOrNullMethodName      = "toOrNull"
  private val castOrConversionMethods = Set(isMethodName, toMethodName, toOrNullMethodName)
  private val stringClass             = classOf[String]

  private val conversionsRegistry: List[Conversion] = List(
    ToLongConversionExt,
    ToDoubleConversionExt,
    ToBigDecimalConversionExt,
    ToBooleanConversionExt,
    ToStringConversion,
    ToMapConversionExt,
    ToListConversionExt,
    ToByteConversion,
    ToShortConversion,
    ToIntegerConversion,
    ToFloatConversion,
    ToBigIntegerConversion,
    FromStringConversion(ZoneOffset.of),
    FromStringConversion(ZoneId.of),
    FromStringConversion((source: String) => {
      val locale = StringUtils.parseLocale(source)
      assert(LocaleUtils.isAvailableLocale(locale)) // without this check even "qwerty" is considered a Locale
      locale
    }),
    FromStringConversion(Charset.forName),
    FromStringConversion(Currency.getInstance),
    FromStringConversion[UUID]((source: String) =>
      if (StringUtils.hasLength(source)) UUID.fromString(source.trim) else null
    ),
    FromStringConversion(LocalTime.parse),
    FromStringConversion(LocalDate.parse),
    FromStringConversion(LocalDateTime.parse),
    FromStringConversion[ChronoLocalDate](LocalDate.parse),
    FromStringConversion[ChronoLocalDateTime[_]](LocalDateTime.parse)
  )

  private val conversionsByType: Map[String, Conversion] = conversionsRegistry
    .flatMap(c => c.resultTypeClass.classByNameAndSimpleNameLowerCase().map(n => n._1 -> c))
    .toMap

  override type ExtensionMethodInvocationTarget = CastOrConversionExt
  override val invocationTargetClass: Class[CastOrConversionExt] = classOf[CastOrConversionExt]

  def isCastOrConversionMethod(methodName: String): Boolean =
    castOrConversionMethods.contains(methodName)

  def allowedConversions(clazz: Class[_]): List[Conversion] = conversionsRegistry.filter(_.appliesToConversion(clazz))

  override def createConverter(
      set: ClassDefinitionSet
  ): ToExtensionMethodInvocationTargetConverter[CastOrConversionExt] = {
    val classesBySimpleName = set.classDefinitionsMap.keySet.classesByNamesAndSimpleNamesLowerCase()
    (target: Any) => new CastOrConversionExt(target, classesBySimpleName)
  }

  override def extractDefinitions(clazz: Class[_], set: ClassDefinitionSet): Map[String, List[MethodDefinition]] = {
    val castAllowedClasses = clazz.findAllowedClassesForCastParameter(set).mapValuesNow(_.clazzName)
    val isConvertibleClass = conversionsRegistry.exists(_.appliesToConversion(clazz))
    if (castAllowedClasses.nonEmpty || isConvertibleClass) {
      definitions(castAllowedClasses)
    } else {
      Map.empty
    }
  }

  // Convert methods should visible in runtime for every class because we allow invoke convert methods on an unknown
  // object in Typer, but in the runtime the same type could be known and that's why should add convert method for an
  // every class.
  override def appliesToClassInRuntime(clazz: Class[_]): Boolean = true

  private def getConversion(className: String): Either[Throwable, Conversion] =
    conversionsByType.get(className.toLowerCase) match {
      case Some(conversion) => Right(conversion)
      case None             => Left(new IllegalArgumentException(s"Conversion for class $className not found"))
    }

  private def definitions(allowedClasses: Map[Class[_], TypingResult]): Map[String, List[MethodDefinition]] =
    List(
      FunctionalMethodDefinition(
        (target, params) => canConvertToTyping(allowedClasses)(target, params),
        methodTypeInfoWithStringParam(Typed.typedClass[JBoolean]),
        "is",
        Some("Checks if a type can be converted to a given class")
      ),
      FunctionalMethodDefinition(
        (target, params) => convertToTyping(allowedClasses)(target, params),
        methodTypeInfoWithStringParam(Unknown),
        "to",
        Some("Converts a type to a given class or throws exception if type cannot be converted.")
      ),
      FunctionalMethodDefinition(
        (target, params) => convertToTyping(allowedClasses)(target, params),
        methodTypeInfoWithStringParam(Unknown),
        "toOrNull",
        Some("Converts a type to a given class or return null if type cannot be converted.")
      ),
    ).groupBy(_.name)

  private def convertToTyping(allowedClasses: Map[Class[_], TypingResult])(
      invocationTarget: TypingResult,
      arguments: List[TypingResult]
  ): ValidatedNel[GenericFunctionTypingError, typing.TypingResult] = arguments match {
    case TypedObjectWithValue(_, clazzName: String) :: Nil =>
      allowedClasses
        .find(_._1.equalsScalaClassNameIgnoringCase(clazzName))
        .map(_._2.validNel)
        .orElse(getConversion(clazzName).map(_.typingFunction(invocationTarget)).toOption) match {
        case Some(result) => result
        case _ => GenericFunctionTypingError.OtherError(s"Cannot cast or convert to: '$clazzName'").invalidNel
      }
    case _ => GenericFunctionTypingError.ArgumentTypeError.invalidNel
  }

  private def canConvertToTyping(allowedClasses: Map[Class[_], TypingResult])(
      invocationTarget: TypingResult,
      arguments: List[TypingResult]
  ): ValidatedNel[GenericFunctionTypingError, TypingResult] =
    convertToTyping(allowedClasses)(invocationTarget, arguments).map(_ => Typed.typedClass[Boolean])

  private def methodTypeInfoWithStringParam(result: TypingResult) = MethodTypeInfo(
    noVarArgs = List(
      Parameter("className", Typed.genericTypeClass(stringClass, Nil))
    ),
    varArg = None,
    result = result
  )

}
