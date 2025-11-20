package pl.touk.nussknacker.engine.api.json.encoders

import cats.data.ValidatedNel
import cats.implicits.{catsSyntaxValidatedId, toTraverseOps}
import io.circe.{Encoder, Json}
import io.circe.Encoder.encodeZonedDateTimeWithFormatter
import io.circe.Json.{
  fromBigDecimal,
  fromBigInt,
  fromBoolean,
  fromDouble,
  fromFloat,
  fromInt,
  fromLong,
  fromString,
  Null
}
import pl.touk.nussknacker.engine.api.DisplayJson

import java.math.BigInteger
import java.nio.charset.Charset
import java.time._
import java.time.format.DateTimeFormatter
import java.util.{Currency, Locale, UUID}
import scala.annotation.tailrec
import scala.jdk.CollectionConverters._

private[encoders] trait ToJsonEncoderWithFallback {

  @tailrec
  final def encodeValue(value: Any): ValidatedNel[String, Json] = value match {
    case null                        => Json.Null.validNel
    case Some(a)                     => encodeValue(a)
    case None                        => Null.validNel
    case value: Json                 => value.validNel
    case value: Int                  => fromInt(value).validNel
    case value: Short                => fromInt(value).validNel
    case value: Long                 => fromLong(value).validNel
    case value: Boolean              => fromBoolean(value).validNel
    case value: String               => fromString(value).validNel
    case value: UUID                 => fromString(value.toString).validNel
    case value: Byte                 => fromInt(value).validNel
    case value: BigInteger           => fromBigInt(value).validNel
    case value: BigInt               => fromBigInt(value.underlying()).validNel
    case value: java.math.BigDecimal => fromBigDecimal(value).validNel
    case value: BigDecimal           => fromBigDecimal(value.underlying()).validNel
    case value: Float =>
      fromFloat(value).map(_.validNel).getOrElse(s"Could not encode $value as json.".invalidNel)
    case value: Double =>
      fromDouble(value).map(_.validNel).getOrElse(s"Could not encode $value as json.".invalidNel)
    case value: Number =>
      fromDouble(value.doubleValue()).map(_.validNel).getOrElse(s"Could not encode $value as json.".invalidNel)
    case value: Instant =>
      Encoder[Instant].apply(value).validNel
    // Default implementation serializes to ISO_ZONED_DATE_TIME which is not handled well by some parsers...
    case value: ZonedDateTime =>
      encodeZonedDateTimeWithFormatter(DateTimeFormatter.ISO_OFFSET_DATE_TIME).apply(value).validNel
    case value: OffsetDateTime =>
      Encoder[OffsetDateTime].apply(value).validNel
    case value: LocalDate =>
      Encoder[LocalDate].apply(value).validNel
    case value: LocalTime =>
      Encoder[LocalTime].apply(value).validNel
    case value: LocalDateTime =>
      Encoder[LocalDateTime].apply(value).validNel
    case value: Duration =>
      fromString(value.toString).validNel // Duration uses ISO-8601 format by default
    case value: Period =>
      fromString(value.toString).validNel // Period uses ISO-8601 format by default
    case value: ZoneId =>
      fromString(value.toString).validNel
    case value: Locale =>
      fromString(value.toString).validNel
    case value: Charset =>
      fromString(value.toString).validNel
    case value: Currency =>
      fromString(value.toString).validNel
    case map: Map[_, _] =>
      encodeMap(map)
    case map: scala.collection.Map[_, _] =>
      encodeMap(map.toMap)
    case map: java.util.Map[_, _] =>
      encodeMap(map.asScala.toMap)
    case vals: java.util.Collection[_] =>
      encodeIterable(vals.asScala)
    case vals: Array[_] =>
      encodeIterable(vals)
    case vals: Iterable[_] =>
      encodeIterable(vals)
    case value: Enum[_] =>
      fromString(value.name()).validNel
    case value: DisplayJson =>
      value.asJson.validNel
    case value =>
      handleUnknownValue(value) match {
        case Some(json) => json.validNel
        case None => s"Encoding of value [$value] of class [${value.getClass.getName}] is not supported".invalidNel
      }
  }

  protected def handleUnknownValue(value: Any): Option[Json]

  private def encodeIterable(vals: Iterable[_]) = {
    val encodedValues = vals.map(elem => encodeValue(elem)).toList.sequence
    encodedValues.map(values => Json.fromValues(values))
  }

  private def encodeMap(map: Map[_, _]) = {
    val encodedFields = map.toList.map { case (key, value) =>
      encodeValue(key).andThen(encodedKey =>
        encodedKey.asString orElse encodedKey.asNumber.map(_.toString) orElse encodedKey.asBoolean.map(
          _.toString
        ) match {
          case Some(encodedKeyString) =>
            encodeValue(value).map(encodedValue => encodedKeyString -> encodedValue)
          case None =>
            s"Failed to encode Record key '$encodedKey' as String".invalidNel
        }
      )
    }
    encodedFields.sequence.map(values => Json.fromFields(values))
  }

}
