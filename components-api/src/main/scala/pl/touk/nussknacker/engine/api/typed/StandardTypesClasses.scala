package pl.touk.nussknacker.engine.api.typed

import java.lang
import java.math.BigInteger
import java.nio.charset.Charset
import java.time._
import java.util.{Currency, Locale, UUID}

object StandardTypesClasses {

  val BigDecimalClass: Class[java.math.BigDecimal] = classOf[java.math.BigDecimal]
  val DoubleClass: Class[lang.Double]              = classOf[lang.Double]
  val FloatClass: Class[lang.Float]                = classOf[lang.Float]

  // The order is important for determining promoted type (it is from widest to narrowest type)
  private[typed] val FloatingPointNumbersOrderedFromWidestToNarrowest: Seq[Class[_]] = IndexedSeq(
    BigDecimalClass,
    DoubleClass,
    FloatClass
  )

  val BigIntegerClass: Class[BigInteger] = classOf[BigInteger]
  val LongClass: Class[lang.Long]        = classOf[lang.Long]
  val IntegerClass: Class[Integer]       = classOf[Integer]
  val ShortClass: Class[lang.Short]      = classOf[lang.Short]
  val ByteClass: Class[lang.Byte]        = classOf[lang.Byte]

  // The order is important for determining promoted type (it is from widest to narrowest type)
  private[typed] val DecimalNumbersOrderedFromWidestToNarrowest: Seq[Class[_]] = IndexedSeq(
    BigIntegerClass,
    LongClass,
    IntegerClass,
    ShortClass,
    ByteClass
  )

  val NumberClass: Class[_] = classOf[Number]

  val BooleanClass: Class[_] = classOf[java.lang.Boolean]

  val StringClass: Class[_] = classOf[String]

  val ListClass: Class[_] = classOf[java.util.List[_]]

  // In our type system, we don't have a separate class for each array, we use Array of Objects everywhere
  val ArrayClass: Class[_] = classOf[Array[Object]]

  val MapClass: Class[_] = classOf[java.util.Map[_, _]]

  val SetClass: Class[_] = classOf[java.util.Set[_]]

  val AvroIndexedRecordClassName = "org.apache.avro.generic.IndexedRecord"

  val FlinkTableApiRowClassName = "org.apache.flink.types.Row"

  val CollectionClass: Class[_] = classOf[java.util.Collection[_]]

  def isFloatingPointNumber(clazz: Class[_]): Boolean = FloatingPointNumbersOrderedFromWidestToNarrowest.contains(clazz)

  def isDecimalNumber(clazz: Class[_]): Boolean = DecimalNumbersOrderedFromWidestToNarrowest.contains(clazz)

  // date types
  val InstantClass: Class[_]        = classOf[Instant]
  val OffsetDateTimeClass: Class[_] = classOf[OffsetDateTime]
  val ZonedDateTimeClass: Class[_]  = classOf[ZonedDateTime]
  val LocalDateTimeClass: Class[_]  = classOf[LocalDateTime]
  val LocalDateClass: Class[_]      = classOf[LocalDate]
  val LocalTimeClass: Class[_]      = classOf[LocalTime]
  val DurationClass: Class[_]       = classOf[Duration]
  val PeriodClass: Class[_]         = classOf[Period]
  val ZoneOffsetClass: Class[_]     = classOf[ZoneOffset]
  val ZoneIdClass: Class[_]         = classOf[ZoneId]

  // other supported logical types
  val CurrencyClass: Class[_] = classOf[Currency]
  val LocaleClass: Class[_]   = classOf[Locale]
  val UUIDClass: Class[_]     = classOf[UUID]
  val CharsetClass: Class[_]  = classOf[Charset]

}
