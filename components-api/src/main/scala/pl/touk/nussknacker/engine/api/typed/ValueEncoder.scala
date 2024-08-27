package pl.touk.nussknacker.engine.api.typed

import cats.data.ValidatedNel
import cats.implicits.{catsSyntaxValidatedId, toTraverseOps}
import io.circe.Json
import io.circe.Json.{fromBigDecimal, fromBigInt, fromBoolean, fromDouble, fromFloat, fromInt, fromLong, fromString}

import java.math.BigInteger
import java.time.{Duration, LocalDate, LocalDateTime, LocalTime, Period}
import java.time.format.DateTimeFormatter
import scala.jdk.CollectionConverters._

object ValueEncoder {

  def encodeValue(value: Any): ValidatedNel[String, Json] = value match {
    case null                        => Json.Null.validNel
    case value: Int                  => fromInt(value).validNel
    case value: Short                => fromInt(value).validNel
    case value: Long                 => fromLong(value).validNel
    case value: Boolean              => fromBoolean(value).validNel
    case value: String               => fromString(value).validNel
    case value: Byte                 => fromInt(value).validNel
    case value: BigInteger           => fromBigInt(value).validNel
    case value: java.math.BigDecimal => fromBigDecimal(value).validNel
    case value: Float =>
      fromFloat(value).map(_.validNel).getOrElse(s"Could not encode $value as json.".invalidNel)
    case value: Double =>
      fromDouble(value).map(_.validNel).getOrElse(s"Could not encode $value as json.".invalidNel)

    case value: LocalDateTime =>
      fromString(value.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)).validNel
    case value: LocalDate =>
      fromString(value.format(DateTimeFormatter.ISO_LOCAL_DATE)).validNel
    case value: LocalTime =>
      fromString(value.format(DateTimeFormatter.ISO_LOCAL_TIME)).validNel
    case value: Duration =>
      fromString(value.toString).validNel // Duration uses ISO-8601 format by default
    case value: Period =>
      fromString(value.toString).validNel // Period uses ISO-8601 format by default

    case vals: java.util.Collection[_] =>
      val encodedValues = vals.asScala.map(elem => encodeValue(elem)).toList.sequence
      encodedValues.map(values => Json.fromValues(values))
    case vals: java.util.Map[_, _] =>
      val encodedFields = vals.asScala.toList.map { case (key, value) =>
        encodeValue(key).andThen(encodedKey =>
          encodedKey.asString match {
            case Some(encodedKeyString) =>
              encodeValue(value).map(encodedValue => encodedKeyString -> encodedValue)
            case None =>
              s"Failed to encode Record key '$encodedKey' as String".invalidNel
          }
        )
      }

      encodedFields.sequence.map(values => Json.fromFields(values))
    case value =>
      s"Encoding of value [$value] of class [${value.getClass.getName}] is not supported".invalidNel
  }

}
