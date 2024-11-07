package pl.touk.nussknacker.engine.api.typed

import org.apache.commons.lang3.{ClassUtils, LocaleUtils}
import org.springframework.util.StringUtils
import pl.touk.nussknacker.engine.api.typed.supertype.NumberTypesPromotionStrategy
import pl.touk.nussknacker.engine.api.typed.typing.{SingleTypingResult, TypedClass, TypedObjectWithValue}

import java.nio.charset.Charset
import java.time._
import java.time.chrono.{ChronoLocalDate, ChronoLocalDateTime}
import java.util.{Currency, UUID}
import scala.reflect.{ClassTag, classTag}
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

    def canConvert(value: String, superclassCandidate: TypedClass): Boolean = {
      ClassUtils.isAssignable(superclassCandidate.klass, klass, true) && Try(
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

  def canBeConvertedTo(givenType: SingleTypingResult, superclassCandidate: TypedClass): Boolean = {
    handleNumberConversions(givenType.runtimeObjType, superclassCandidate) ||
    handleStringToValueClassConversions(givenType, superclassCandidate)
  }

  // See org.springframework.core.convert.support.NumberToNumberConverterFactory
  private def handleNumberConversions(givenClass: TypedClass, superclassCandidate: TypedClass): Boolean = {
    val boxedGivenClass          = ClassUtils.primitiveToWrapper(givenClass.klass)
    val boxedSuperclassCandidate = ClassUtils.primitiveToWrapper(superclassCandidate.klass)
    // We can't check precision here so we need to be loose here
    // TODO: Add feature flag: strictNumberPrecisionChecking (default false?)

    def isFloating(candidate: Class[_]): Boolean = {
      NumberTypesPromotionStrategy.isFloatingNumber(candidate) || candidate == classOf[java.math.BigDecimal]
    }
    def isDecimalNumber(candidate: Class[_]): Boolean = {
      NumberTypesPromotionStrategy.isDecimalNumber(candidate)
    }

    boxedSuperclassCandidate match {
      case candidate if isFloating(candidate) =>
        ClassUtils.isAssignable(boxedGivenClass, classOf[Number], true)

      case candidate if isDecimalNumber(candidate) =>
        import ImplicitConversionDeterminer.isAssignable
        isAssignable(boxedGivenClass, candidate)

      case _ => false
    }

  }

  private def handleStringToValueClassConversions(
      givenType: SingleTypingResult,
      superclassCandidate: TypedClass
  ): Boolean =
    givenType match {
      case TypedObjectWithValue(_, str: String) =>
        stringConversions.exists(_.canConvert(str, superclassCandidate))
      case _ => false
    }

}
