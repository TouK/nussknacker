package pl.touk.nussknacker.engine.api.typed

import org.apache.commons.lang3.ClassUtils
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
  // TODO: Add feature flag: strictBigDecimalChecking (default false?) and rename strictTypeChecking to strictClassesTypeChecking
  private val ConversionFromClassesForDecimals = NumberTypesPromotionStrategy.DecimalNumbers.toSet + classOf[java.math.BigDecimal]

  val stringConversionsMap: Map[Class[_], String => Object] = Map(
    classOf[ZoneId] -> ((source: String) => ZoneId.of(source)),
    classOf[ZoneOffset] -> ((source: String) => ZoneOffset.of(source)),
    classOf[Locale] -> ((source: String) => StringUtils.parseLocale(source)),
    classOf[Charset] -> ((source: String) => Charset.forName(source)),
    classOf[Currency] -> ((source: String) => Currency.getInstance(source)),
    classOf[UUID] -> ((source: String) => if (StringUtils.hasLength(source)) UUID.fromString(source.trim) else null),
    classOf[LocalTime] -> ((source: String) => LocalTime.parse(source)),
    classOf[LocalDate] -> ((source: String) => LocalDate.parse(source)),
    classOf[LocalDateTime] -> ((source: String) => LocalDateTime.parse(source)),
    classOf[ChronoLocalDate] -> ((source: String) => LocalDate.parse(source)),
    classOf[ChronoLocalDateTime[_]] -> ((source: String) => LocalDateTime.parse(source)),
    classOf[ChronoLocalDateTime[_]] -> ((source: String) => LocalDateTime.parse(source)),
  )

  private def valueClassesThatBeConvertedFromString(typed: TypedObjectWithValue): List[Class[_]] = {
    stringConversionsMap.filter { case (_, conversion) =>
      try {
        conversion(typed.value.asInstanceOf[String])
        true
      } catch {
        case _: Throwable => false
      }
    }.keys.toList
  }

  def canBeConvertedTo(givenType: SingleTypingResult, superclassCandidate: TypedClass): Boolean = {
    handleNumberConversions(givenType.objType, superclassCandidate) ||
      handleStringToValueClassConversions(givenType, superclassCandidate)
  }

  // See org.springframework.core.convert.support.NumberToNumberConverterFactory
  private def handleNumberConversions(givenClass: TypedClass, superclassCandidate: TypedClass): Boolean = {
    val boxedGivenClass = ClassUtils.primitiveToWrapper(givenClass.klass)
    val boxedSuperclassCandidate = ClassUtils.primitiveToWrapper(superclassCandidate.klass)
    // We can't check precision here so we need to be loose here
    // TODO: Add feature flag: strictNumberPrecisionChecking (default false?) and rename strictTypeChecking to strictClassesTypeChecking
    if (NumberTypesPromotionStrategy.isFloatingNumber(boxedSuperclassCandidate) || boxedSuperclassCandidate == classOf[java.math.BigDecimal]) {
      ClassUtils.isAssignable(boxedGivenClass, classOf[Number], true)
    } else if (NumberTypesPromotionStrategy.isDecimalNumber(boxedSuperclassCandidate)) {
      ConversionFromClassesForDecimals.exists(ClassUtils.isAssignable(boxedGivenClass, _, true))
    } else {
      false
    }
  }

  private def handleStringToValueClassConversions(givenType: SingleTypingResult, superclassCandidate: TypedClass): Boolean = {
    givenType.isInstanceOf[TypedObjectWithValue] &&
      valueClassesThatBeConvertedFromString(givenType.asInstanceOf[TypedObjectWithValue]).exists(ClassUtils.isAssignable(superclassCandidate.klass, _, true))
  }

}
