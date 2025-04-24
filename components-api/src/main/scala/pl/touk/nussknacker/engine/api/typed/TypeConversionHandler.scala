package pl.touk.nussknacker.engine.api.typed

import org.apache.commons.lang3.{ClassUtils, LocaleUtils}
import org.springframework.util.StringUtils
import pl.touk.nussknacker.engine.api.typed.ConversionStrategy.{Loose, Strict}
import pl.touk.nussknacker.engine.api.typed.supertype.NumberTypesPromotionStrategy
import pl.touk.nussknacker.engine.api.typed.typing.{SingleTypingResult, TypedClass, TypedObjectWithValue}
import pl.touk.nussknacker.engine.api.typed.typing.{superTypeOfTypes, Typed, TypedObjectTypingResult, Unknown}
import pl.touk.nussknacker.engine.util.AssignabilityUtil

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
private[engine] object TypeConversionHandler {

  private val javaListClass      = classOf[java.util.List[_]]
  private val arrayOfAnyRefClass = classOf[Array[AnyRef]]

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

  def canBeConverted(from: SingleTypingResult, to: TypedClass)(
      implicit conversionStrategy: NonEmptyConversionStrategy
  ): Boolean = {
    handleImplicitConversion(from, to) ||
    handleNumberConversion(from.runtimeObjType, to) ||
    handleIndexedRecordToMapConversion(from.runtimeObjType, to)
  }

  private def handleImplicitConversion(from: SingleTypingResult, to: TypedClass)(
      implicit conversionStrategy: NonEmptyConversionStrategy
  ) = {
    conversionStrategy match {
      // Implicit conversions are not allowed in strict conversion strategy. We want to behave as plain java, without magical tricks.
      case Strict => false
      case Loose =>
        handleStringToValueClassConversions(from, to) ||
        handleArrayToListConversions(from.runtimeObjType, to)
    }
  }

  private def handleNumberConversion(from: SingleTypingResult, to: TypedClass)(
      implicit conversionStrategy: NonEmptyConversionStrategy
  ) = {
    conversionStrategy match {
      case Strict => handleStrictNumberConversions(from.runtimeObjType.klass, to.klass)
      case Loose  => handleLooseNumberConversion(from.runtimeObjType.klass, to.klass)
    }
  }

  private def handleIndexedRecordToMapConversion(
      from: SingleTypingResult,
      to: TypedClass
  )(implicit conversionStrategy: NonEmptyConversionStrategy): Boolean = {
    if (AssignabilityUtil.isAssignableToLoadableClass(
        from.runtimeObjType.klass,
        "org.apache.avro.generic.IndexedRecord"
      ) && ClassUtils.isAssignable(to.klass, classOf[java.util.Map[_, _]])) {

      val indexedRecordKeyParam = Typed.genericTypeClass(classOf[String], List())
      val indexedRecordValueParam = from match {
        case TypedObjectTypingResult(fromFields, _, _) =>
          superTypeOfTypes(fromFields.values)
        case _ => Unknown
      }

      val (mapKeyParam, mapValueParam) = to match {
        case TypedClass(_, key :: value :: Nil) =>
          (key, value)
        case _ => (Unknown, Unknown)
      }

      AssignabilityDeterminer.isAssignable(indexedRecordKeyParam, mapKeyParam).isValid &&
      AssignabilityDeterminer
        .isAssignable(indexedRecordValueParam, mapValueParam)
        .isValid
    } else {
      false
    }
  }

  // See org.springframework.core.convert.support.NumberToNumberConverterFactory
  private def handleLooseNumberConversion(from: Class[_], to: Class[_]): Boolean = {
    val boxedGivenClass          = ClassUtils.primitiveToWrapper(from)
    val boxedSuperclassCandidate = ClassUtils.primitiveToWrapper(to)

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
    (Option(ClassUtils.wrapperToPrimitive(givenClass)), Option(ClassUtils.wrapperToPrimitive(to))) match {
      case (Some(givenPrimitive), Some(toPrimitive)) => ClassUtils.isAssignable(givenPrimitive, toPrimitive)
      case (_, _)                                    => false
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

  // See pl.touk.nussknacker.engine.spel.internal.ArrayToListConverter
  private def handleArrayToListConversions(from: TypedClass, to: TypedClass): Boolean = {
    (from, to) match {
      // Generic type parameters are checked in AssignabilityDeterminer
      case (TypedClass(`arrayOfAnyRefClass`, _), TypedClass(`javaListClass`, _)) =>
        true
      case _ =>
        false
    }
  }

}

private[engine] sealed trait ConversionStrategy

private[engine] sealed trait NonEmptyConversionStrategy extends ConversionStrategy

private[engine] object ConversionStrategy {

  object NoConversion extends ConversionStrategy

  object Strict extends NonEmptyConversionStrategy

  object Loose extends NonEmptyConversionStrategy

}
