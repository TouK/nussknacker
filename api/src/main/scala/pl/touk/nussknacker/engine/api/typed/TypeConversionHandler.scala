package pl.touk.nussknacker.engine.api.typed

import org.apache.commons.lang3.ClassUtils
import pl.touk.nussknacker.engine.api.typed.supertype.NumberTypesPromotionStrategy
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedClass}

import java.nio.charset.Charset
import java.util.{Currency, Locale, TimeZone, UUID}

/**
  * This class handle conversion logic which is done in SpEL's org.springframework.expression.TypeConverter
  * See org.springframework.core.convert.support.DefaultConversionService for full conversion lists
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

  private val ValueClassesThatBeConvertedFromString = List[Class[_]](classOf[TimeZone], classOf[Locale], classOf[Charset], classOf[Currency], classOf[UUID])

  def canBeConvertedTo(givenClass: TypedClass, superclassCandidate: TypedClass): Boolean = {
    handleNumberConversions(givenClass, superclassCandidate) ||
      handleStringToValueClassConversions(givenClass, superclassCandidate)
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

  private def handleStringToValueClassConversions(givenClass: TypedClass, superclassCandidate: TypedClass): Boolean = {
    givenClass == Typed[String] && ValueClassesThatBeConvertedFromString.exists(ClassUtils.isAssignable(superclassCandidate.klass, _, true))
  }

}
