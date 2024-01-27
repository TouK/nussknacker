package pl.touk.nussknacker.engine.api.typed

import org.apache.commons.lang3.{ClassUtils, LocaleUtils}
import pl.touk.nussknacker.engine.api.typed.supertype.NumberTypesPromotionStrategy
import pl.touk.nussknacker.engine.api.typed.typing.{SingleTypingResult, TypedClass, TypedObjectWithValue}

import java.nio.charset.Charset
import java.time._
import java.time.chrono.{ChronoLocalDate, ChronoLocalDateTime}
import java.util.{Currency, Locale, UUID}
import org.springframework.util.StringUtils

/**
  * This class handle conversion logic which is done in SpEL's org.springframework.expression.TypeConverter.
  * See pl.touk.nussknacker.engine.spel.internal.DefaultSpelConversionsProvider for full conversion list
  */
object TypeConversionHandler {

  /**
    * java.math.BigDecimal is quite often returned as a wrapper for all kind of numbers (floating and without floating point).
    * Given to this we cannot to be sure if conversion is safe or not based on type (without scale knowledge).
    * So we have two options: enforce user to convert to some type without floating point (e.g. BigInteger) or be loose in this point.
    * Be default we will be loose.
    */
  // TODO: Add feature flag: strictBigDecimalChecking (default false?)
  private val ConversionFromClassesForDecimals =
    NumberTypesPromotionStrategy.DecimalNumbers.toSet + classOf[java.math.BigDecimal]

  case class StringConversion[T](klass: Class[T], conversion: String => T)

  val stringConversions: Seq[StringConversion[_]] = List(
    StringConversion(classOf[ZoneId], (source: String) => ZoneId.of(source)),
    StringConversion(classOf[ZoneOffset], (source: String) => ZoneOffset.of(source)),
    StringConversion(
      classOf[Locale],
      (source: String) => {
        val locale = StringUtils.parseLocale(source)
        assert(LocaleUtils.isAvailableLocale(locale)) // without this check even "qwerty" is considered a Locale
        locale
      }
    ),
    StringConversion(classOf[Charset], (source: String) => Charset.forName(source)),
    StringConversion(classOf[Currency], (source: String) => Currency.getInstance(source)),
    StringConversion(
      classOf[UUID],
      (source: String) => if (StringUtils.hasLength(source)) UUID.fromString(source.trim) else null
    ),
    StringConversion(classOf[LocalTime], (source: String) => LocalTime.parse(source)),
    StringConversion(classOf[LocalDate], (source: String) => LocalDate.parse(source)),
    StringConversion(classOf[LocalDateTime], (source: String) => LocalDateTime.parse(source)),
    StringConversion(classOf[ChronoLocalDate], (source: String) => LocalDate.parse(source)),
    StringConversion(classOf[ChronoLocalDateTime[_]], (source: String) => LocalDateTime.parse(source))
  )

  private def valueClassCanBeConvertedFromString(
      typed: TypedObjectWithValue,
      superclassCandidate: TypedClass
  ): Boolean =
    stringConversions.exists { case StringConversion(klass, conversion) =>
      try {
        conversion(typed.value.asInstanceOf[String])
        ClassUtils.isAssignable(superclassCandidate.klass, klass, true)
      } catch {
        case _: Throwable => false
      }
    }

  def canBeConvertedTo(givenType: SingleTypingResult, superclassCandidate: TypedClass): Boolean = {
    handleNumberConversions(givenType.objType, superclassCandidate) ||
    handleStringToValueClassConversions(givenType, superclassCandidate)
  }

  // See org.springframework.core.convert.support.NumberToNumberConverterFactory
  private def handleNumberConversions(givenClass: TypedClass, superclassCandidate: TypedClass): Boolean = {
    val boxedGivenClass          = ClassUtils.primitiveToWrapper(givenClass.klass)
    val boxedSuperclassCandidate = ClassUtils.primitiveToWrapper(superclassCandidate.klass)
    // We can't check precision here so we need to be loose here
    // TODO: Add feature flag: strictNumberPrecisionChecking (default false?)
    if (NumberTypesPromotionStrategy
        .isFloatingNumber(boxedSuperclassCandidate) || boxedSuperclassCandidate == classOf[java.math.BigDecimal]) {
      ClassUtils.isAssignable(boxedGivenClass, classOf[Number], true)
    } else if (NumberTypesPromotionStrategy.isDecimalNumber(boxedSuperclassCandidate)) {
      ConversionFromClassesForDecimals.exists(ClassUtils.isAssignable(boxedGivenClass, _, true))
    } else {
      false
    }
  }

  private def handleStringToValueClassConversions(
      givenType: SingleTypingResult,
      superclassCandidate: TypedClass
  ): Boolean =
    givenType match {
      case objectWithValue: TypedObjectWithValue =>
        valueClassCanBeConvertedFromString(objectWithValue, superclassCandidate)
      case _ => false
    }

}
