package pl.touk.nussknacker.engine.api.typed

import org.apache.commons.lang3.{ClassUtils, LocaleUtils}
import org.springframework.util.StringUtils
import pl.touk.nussknacker.engine.api.typed.ConversionStrategy.{Loose, Strict}
import pl.touk.nussknacker.engine.api.typed.StandardTypesClasses._
import pl.touk.nussknacker.engine.api.typed.supertype.CommonSupertypeFinder.Default.superTypeOfTypes
import pl.touk.nussknacker.engine.api.typed.typing._
import pl.touk.nussknacker.engine.util.AssignabilityUtil

import java.nio.charset.Charset
import java.time._
import java.time.chrono.{ChronoLocalDate, ChronoLocalDateTime}
import java.util.{Currency, UUID}
import scala.collection.compat._
import scala.reflect.{classTag, ClassTag}
import scala.util.Try

/**
  * This class handle conversion logic which is done in SpEL's org.springframework.expression.TypeConverter.
  * See pl.touk.nussknacker.engine.spel.internal.DefaultSpelConversionsProvider for full conversion list
  */
private[engine] object TypeConversionHandler {

  private val toMapConvertibleClassNames = List(AvroIndexedRecordClassName, FlinkTableApiRowClassName)

  /**
    * java.math.BigDecimal is quite often returned as a wrapper for all kind of numbers (floating and without floating point).
    * Given to this we cannot be sure if conversion is safe or not based on type (without scale knowledge).
    * So we have two options: force user to convert to some type without floating point (e.g. BigInteger) or be loose in this point.
    * Be default we will be loose.
    */
  // TODO: Add feature flag: strictBigDecimalChecking (default false?)
  private val toDecimalConvertibleClasses =
    StandardTypesClasses.DecimalNumbersOrderedFromWidestToNarrowest.toSet + BigDecimalClass

  private val fromLiteralStringValueConversions: List[FromLiteralStringValueConversion[_]] = List(
    new FromLiteralStringValueConversion(ZoneOffset.of),
    new FromLiteralStringValueConversion(ZoneId.of),
    new FromLiteralStringValueConversion((source: String) => {
      val locale = StringUtils.parseLocale(source)
      assert(LocaleUtils.isAvailableLocale(locale)) // without this check even "qwerty" is considered a Locale
      locale
    }),
    new FromLiteralStringValueConversion(Charset.forName),
    new FromLiteralStringValueConversion(Currency.getInstance),
    new FromLiteralStringValueConversion[UUID]((source: String) =>
      if (StringUtils.hasLength(source)) UUID.fromString(source.trim) else null
    ),
    new FromLiteralStringValueConversion(LocalTime.parse),
    new FromLiteralStringValueConversion(LocalDate.parse),
    new FromLiteralStringValueConversion(LocalDateTime.parse),
    // SpEL GenericConversionService need a specific class, so we need both conversions for LocalDate/LocalDateTime and for Chrono*
    new FromLiteralStringValueConversion[ChronoLocalDate](LocalDate.parse),
    new FromLiteralStringValueConversion[ChronoLocalDateTime[_]](LocalDateTime.parse),
    new FromLiteralStringValueConversion[ZonedDateTime](ZonedDateTime.parse),
    new FromLiteralStringValueConversion[OffsetDateTime](OffsetDateTime.parse),
    new FromLiteralStringValueConversion[Instant](Instant.parse),
    new FromLiteralStringValueConversion[Period](Period.parse),
    new FromLiteralStringValueConversion[Duration](Duration.parse),
  )

  private val dateTypesConversions: List[SimpleTypeConversion[_, _]] = List(
    // to Instant
    new SimpleTypeConversion[java.lang.Long, Instant](millis => Instant.ofEpochMilli(millis)),
    new SimpleTypeConversion[ZonedDateTime, Instant](_.toInstant),
    new SimpleTypeConversion[OffsetDateTime, Instant](_.toInstant),
    // to OffsetDateTime
    new SimpleTypeConversion[ZonedDateTime, OffsetDateTime](_.toOffsetDateTime),
    // to LocalDateTime
    new SimpleTypeConversion[ZonedDateTime, LocalDateTime](_.toLocalDateTime),
    new SimpleTypeConversion[OffsetDateTime, LocalDateTime](_.toLocalDateTime),
    // SpEL GenericConversionService need a specific class, so we need both conversions for LocalDate/LocalDateTime and for Chrono*
    new SimpleTypeConversion[ZonedDateTime, ChronoLocalDateTime[_]](_.toLocalDateTime),
    new SimpleTypeConversion[OffsetDateTime, ChronoLocalDateTime[_]](_.toLocalDateTime),
  )

  val allSimpleTypesConversions: List[SimpleTypeConversion[_, _]] =
    fromLiteralStringValueConversions ::: dateTypesConversions

  def canBeConverted(from: SingleTypingResult, to: TypedClass)(
      implicit conversionStrategy: NonEmptyConversionStrategy
  ): Option[SingleTypingResult] = {
    if (from.runtimeObjType == to) {
      None
    } else {
      handleImplicitConversion(from, to) orElse
        Option.when(handleNumberConversion(from.runtimeObjType, to))(to)
    }
  }

  private def handleImplicitConversion(from: SingleTypingResult, to: TypedClass)(
      implicit conversionStrategy: NonEmptyConversionStrategy
  ) = {
    conversionStrategy match {
      // Implicit conversions are not allowed in strict conversion strategy. We want to behave as plain java, without magical tricks.
      case Strict => None
      case Loose =>
        Option.when(handleStringToValueClassConversions(from, to))(to) orElse
          Option.when(handleDateTypesConversions(from.runtimeObjType, to))(to) orElse
          handleArrayToListConversions(from.runtimeObjType, to) orElse
          handleMapConversions(from, to)
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

  // See org.springframework.core.convert.support.NumberToNumberConverterFactory
  private def handleLooseNumberConversion(from: Class[_], to: Class[_]): Boolean = {
    val boxedGivenClass          = ClassUtils.primitiveToWrapper(from)
    val boxedSuperclassCandidate = ClassUtils.primitiveToWrapper(to)

    // We can't check precision here so we need to be loose here
    if (StandardTypesClasses
        .isFloatingPointNumber(boxedSuperclassCandidate) || boxedSuperclassCandidate == BigDecimalClass) {
      ClassUtils.isAssignable(boxedGivenClass, NumberClass)
    } else if (StandardTypesClasses.isDecimalNumber(boxedSuperclassCandidate)) {
      toDecimalConvertibleClasses.exists(ClassUtils.isAssignable(boxedGivenClass, _))
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
        fromLiteralStringValueConversions.exists(_.canConvertValue(str, to))
      case _ => false
    }

  private def handleDateTypesConversions(from: SingleTypingResult, to: TypedClass): Boolean = {
    dateTypesConversions.exists(_.canConvert(from.runtimeObjType, to))
  }

  // See pl.touk.nussknacker.engine.spel.internal.ArrayToListConverter
  private def handleArrayToListConversions(from: TypedClass, to: TypedClass): Option[SingleTypingResult] = {
    (from, to) match {
      // Generic type parameters are checked in AssignabilityDeterminer
      case (
            TypedClass(`ArrayClass`, genericParam :: Nil),
            TypedClass(`ListClass` | `CollectionClass`, _)
          ) =>
        Some(Typed.genericTypeClass(ListClass, genericParam :: Nil))
      case _ =>
        None
    }
  }

  private def handleMapConversions(
      from: SingleTypingResult,
      to: TypedClass
  )(
      implicit conversionStrategy: NonEmptyConversionStrategy
  ): Option[SingleTypingResult] =
    (from.withoutValue, to) match {
      case (
            TypedObjectTypingResult(fromFields, TypedClass(fromRuntimeObjClass, _), _),
            TypedClass(MapClass, mapKeyParam :: mapValueParam :: Nil)
          ) =>
        lazy val indexedRecordValueType = superTypeOfTypes(fromFields.values)

        Option.when(
          toMapConvertibleClassNames.exists(className =>
            AssignabilityUtil.isAssignableToLoadableClass(fromRuntimeObjClass, className)
          ) &&
            AssignabilityDeterminer.isAssignable(Typed[String], mapKeyParam).isValid &&
            AssignabilityDeterminer.isAssignable(indexedRecordValueType, mapValueParam).isValid
        )(
          Typed.record(fromFields, Typed.genericTypeClass(MapClass, Typed[String] :: indexedRecordValueType :: Nil))
        )
      case _ => None
    }

  private class FromLiteralStringValueConversion[To: ClassTag](converter: String => To)
      extends SimpleTypeConversion[String, To](converter) {

    def canConvertValue(value: String, to: TypedClass): Boolean = {
      ClassUtils.isAssignable(to.klass, targetClass) && Try(converter(value)).isSuccess
    }

  }

  class SimpleTypeConversion[From: ClassTag, To: ClassTag](val convert: From => To) {

    val sourceClass: Class[From] =
      classTag[From].runtimeClass.asInstanceOf[Class[From]]

    val targetClass: Class[To] =
      classTag[To].runtimeClass.asInstanceOf[Class[To]]

    def canConvert(from: TypedClass, to: TypedClass): Boolean = {
      ClassUtils.isAssignable(from.klass, sourceClass) && ClassUtils.isAssignable(to.klass, targetClass)
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
