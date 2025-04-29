package pl.touk.nussknacker.engine.api.typed

import org.apache.commons.lang3.{ClassUtils, LocaleUtils}
import org.springframework.util.StringUtils
import pl.touk.nussknacker.engine.api.typed.ConversionStrategy.{Loose, Strict}
import pl.touk.nussknacker.engine.api.typed.supertype.CommonSupertypeFinder.Default.superTypeOfTypes
import pl.touk.nussknacker.engine.api.typed.supertype.NumberTypesPromotionStrategy
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

  private val javaListClass            = classOf[java.util.List[_]]
  private val javaCollectionClass      = classOf[java.util.Collection[_]]
  private val javaMapClass             = classOf[java.util.Map[_, _]]
  private val arrayOfAnyRefClass       = classOf[Array[AnyRef]]
  private val mapConvertableClassNames = List("org.apache.avro.generic.IndexedRecord", "org.apache.flink.types.Row")

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
  private def handleArrayToListConversions(from: TypedClass, to: TypedClass): Option[SingleTypingResult] = {
    (from, to) match {
      // Generic type parameters are checked in AssignabilityDeterminer
      case (
            TypedClass(`arrayOfAnyRefClass`, genericParam :: Nil),
            TypedClass(`javaListClass` | `javaCollectionClass`, _)
          ) =>
        Some(Typed.genericTypeClass(javaListClass, genericParam :: Nil))
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
            TypedClass(`javaMapClass`, mapKeyParam :: mapValueParam :: Nil)
          ) =>
        lazy val indexedRecordValueType = superTypeOfTypes(fromFields.values)

        Option.when(
          mapConvertableClassNames.exists(className =>
            AssignabilityUtil.isAssignableToLoadableClass(fromRuntimeObjClass, className)
          ) &&
            AssignabilityDeterminer.isAssignable(Typed[String], mapKeyParam).isValid &&
            AssignabilityDeterminer.isAssignable(indexedRecordValueType, mapValueParam).isValid
        )(
          Typed.record(fromFields, Typed.genericTypeClass(javaMapClass, Typed[String] :: indexedRecordValueType :: Nil))
        )
      case _ => None
    }

}

private[engine] sealed trait ConversionStrategy

private[engine] sealed trait NonEmptyConversionStrategy extends ConversionStrategy

private[engine] object ConversionStrategy {

  object NoConversion extends ConversionStrategy

  object Strict extends NonEmptyConversionStrategy

  object Loose extends NonEmptyConversionStrategy

}
