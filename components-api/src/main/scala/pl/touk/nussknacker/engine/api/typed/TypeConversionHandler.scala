package pl.touk.nussknacker.engine.api.typed

import org.apache.commons.lang3.{ClassUtils, LocaleUtils}
import org.springframework.util.StringUtils
import pl.touk.nussknacker.engine.api.typed.supertype.NumberTypesPromotionStrategy
import pl.touk.nussknacker.engine.api.typed.supertype.NumberTypesPromotionStrategy.AllNumbers
import pl.touk.nussknacker.engine.api.typed.typing.{SingleTypingResult, TypedClass, TypedObjectWithValue}

import java.math.BigInteger
import java.nio.charset.Charset
import java.time._
import java.time.chrono.{ChronoLocalDate, ChronoLocalDateTime}
import java.util.{Currency, UUID}
import scala.reflect.{classTag, ClassTag}
import scala.util.Try

/**
  * This class handle conversion logic which is done in SpEL's org.springframework.expression.TypeConverter.
  * See pl.touk.nussknacker.engine.spel.internal.DefaultSpelConversionsProvider for full conversion list
  */
object TypeConversionHandler {

  /**
    * java.math.BigDecimal is quite often returned as a wrapper for all kind of numbers (floating and without floating point).
    * Given to this we cannot be sure if conversion is safe or not based on type (without scale knowledge).
    * So we have two options: force user to convert to some type without floating point (e.g. BigInteger) or be loose in this point.
    * Be default we will be loose.
    */
  // TODO: Add feature flag: strictBigDecimalChecking (default false?)
  private val ConversionFromClassesForDecimals =
    NumberTypesPromotionStrategy.DecimalNumbers.toSet + classOf[java.math.BigDecimal]

  case class StringConversion[T: ClassTag](convert: String => T) {

    def klass: Class[T] = {
      val cl = classTag[T].runtimeClass.asInstanceOf[Class[T]]
      cl
    }

    def canConvert(value: String, to: TypedClass): Boolean = {
      ClassUtils.isAssignable(to.klass, klass, true) && Try(
        convert(value)
      ).isSuccess
    }

  }

  val stringConversions: List[StringConversion[_]] = List(
    StringConversion(ZoneOffset.of),
    StringConversion(ZoneId.of),
    StringConversion((source: String) => {
      val locale = StringUtils.parseLocale(source)
      assert(LocaleUtils.isAvailableLocale(locale)) // without this check even "qwerty" is considered a Locale
      locale
    }),
    StringConversion(Charset.forName),
    StringConversion(Currency.getInstance),
    StringConversion[UUID]((source: String) =>
      if (StringUtils.hasLength(source)) UUID.fromString(source.trim) else null
    ),
    StringConversion(LocalTime.parse),
    StringConversion(LocalDate.parse),
    StringConversion(LocalDateTime.parse),
    StringConversion[ChronoLocalDate](LocalDate.parse),
    StringConversion[ChronoLocalDateTime[_]](LocalDateTime.parse)
  )

  def canBeLooselyConvertedTo(from: SingleTypingResult, to: TypedClass): Boolean =
    canBeConvertedToAux(from, to)

  def canBeStrictlyConvertedTo(from: SingleTypingResult, to: TypedClass): Boolean =
    canBeConvertedToAux(from, to, strict = true)

  private def canBeConvertedToAux(from: SingleTypingResult, to: TypedClass, strict: Boolean = false) = {
    handleStringToValueClassConversions(from, to) ||
    handleNumberConversion(from.runtimeObjType, to, strict)
  }

  private def handleNumberConversion(from: SingleTypingResult, to: TypedClass, strict: Boolean) = {
    val boxedGivenClass          = ClassUtils.primitiveToWrapper(from.runtimeObjType.klass)
    val boxedSuperclassCandidate = ClassUtils.primitiveToWrapper(to.klass)

    if (strict)
      handleStrictNumberConversions(boxedGivenClass, boxedSuperclassCandidate)
    else
      handleLooseNumberConversion(boxedGivenClass, boxedSuperclassCandidate)
  }

  // See org.springframework.core.convert.support.NumberToNumberConverterFactory
  private def handleLooseNumberConversion(
      boxedGivenClass: Class[_],
      boxedSuperclassCandidate: Class[_]
  ): Boolean = {
    // We can't check precision here so we need to be loose here
    if (NumberTypesPromotionStrategy
        .isFloatingNumber(boxedSuperclassCandidate) || boxedSuperclassCandidate == classOf[java.math.BigDecimal]) {
      ClassUtils.isAssignable(boxedGivenClass, classOf[Number], true)
    } else if (NumberTypesPromotionStrategy.isDecimalNumber(boxedSuperclassCandidate)) {
      ConversionFromClassesForDecimals.exists(ClassUtils.isAssignable(boxedGivenClass, _, true))
    } else {
      false
    }
  }

  private def handleStrictNumberConversions(givenClass: Class[_], to: Class[_]): Boolean = {
    (givenClass, to) match {
      case (bigInteger, t)
          if (bigInteger == classOf[BigInteger] && (t == classOf[BigDecimal] || t == classOf[BigInteger])) =>
        true
      case (f, t) if (AllNumbers.contains(f) && AllNumbers.contains(t)) =>
        AllNumbers.indexOf(f) >= AllNumbers.indexOf(t)
      case _ => false

    }
  }

  private def handleStringToValueClassConversions(
      from: SingleTypingResult,
      to: TypedClass
  ): Boolean =
    from match {
      case TypedObjectWithValue(_, str: String) =>
        stringConversions.exists(_.canConvert(str, to))
      case _ => false
    }

}
