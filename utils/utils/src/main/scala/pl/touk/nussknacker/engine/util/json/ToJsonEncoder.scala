package pl.touk.nussknacker.engine.util.json

import io.circe.Encoder.encodeZonedDateTimeWithFormatter

import java.time.{Instant, LocalDate, LocalDateTime, LocalTime, OffsetDateTime, ZonedDateTime}
import java.time.format.DateTimeFormatter
import io.circe.{Encoder, Json}
import io.circe.Json._
import pl.touk.nussknacker.engine.api.DisplayJson
import java.util.ServiceLoader
import java.util.UUID
import scala.jdk.CollectionConverters._

object ToJsonEncoder {

  val defaultForTests: ToJsonEncoder = ToJsonEncoder(failOnUnknown = true, getClass.getClassLoader)

}

case class ToJsonEncoder(
    failOnUnknown: Boolean,
    classLoader: ClassLoader,
    highPriority: PartialFunction[Any, Json] = Map()
) {

  private val safeString     = safeJson[String](fromString)
  private val safeLong       = safeJson[Long](fromLong)
  private val safeInt        = safeJson[Int](fromInt)
  private val safeDouble     = safeJson[Double](fromDoubleOrNull)
  private val safeFloat      = safeJson[Float](fromFloatOrNull)
  private val safeBigDecimal = safeJson[java.math.BigDecimal](a => fromBigDecimal(a))
  private val safeBigInt     = safeJson[java.math.BigInteger](a => fromBigInt(a))
  private val safeNumber     = safeJson[Number](a => fromDoubleOrNull(a.doubleValue())) // is it correct?

  private val optionalCustomisations =
    ServiceLoader.load(classOf[ToJsonEncoderCustomisation], classLoader).asScala.map(_.encoder(this.encode))

  def encode(obj: Any): Json = optionalCustomisations
    .foldLeft(highPriority)(_.orElse(_))
    .applyOrElse(
      obj,
      (any: Any) =>
        any match {
          case null                    => Null
          case Some(a)                 => encode(a)
          case None                    => Null
          case j: Json                 => j
          case s: String               => safeString(s)
          case a: Long                 => safeLong(a)
          case a: Double               => safeDouble(a)
          case a: Float                => safeFloat(a)
          case a: Int                  => safeInt(a)
          case a: java.math.BigDecimal => safeBigDecimal(a)
          case a: BigDecimal           => safeBigDecimal(a.underlying())
          case a: java.math.BigInteger => safeBigInt(a)
          case a: BigInt               => safeBigInt(a.underlying())
          case a: Number               => safeNumber(a.doubleValue())
          case a: Boolean              => safeJson[Boolean](fromBoolean)(a)
          case a: LocalDate            => Encoder[LocalDate].apply(a)
          case a: LocalTime            => Encoder[LocalTime].apply(a)
          case a: LocalDateTime        => Encoder[LocalDateTime].apply(a)
          // Default implementation serializes to ISO_ZONED_DATE_TIME which is not handled well by some parsers...
          case a: ZonedDateTime  => encodeZonedDateTimeWithFormatter(DateTimeFormatter.ISO_OFFSET_DATE_TIME).apply(a)
          case a: Instant        => Encoder[Instant].apply(a)
          case a: OffsetDateTime => Encoder[OffsetDateTime].apply(a)
          case a: UUID           => safeString(a.toString)
          case a: DisplayJson    => a.asJson
          case a: scala.collection.Map[_, _] => encodeMap(a.toMap)
          case a: java.util.Map[_, _]        => encodeMap(a.asScala.toMap)
          case a: Iterable[_]                => fromValues(a.map(encode))
          case a: Enum[_]                    => safeString(a.toString)
          case a: java.util.Collection[_]    => fromValues(a.asScala.map(encode))
          case a: Array[_]                   => fromValues(a.map(encode))
          case _ if !failOnUnknown           => safeString(any.toString)
          case a                             => throw new IllegalArgumentException(s"Invalid type: ${a.getClass}")
        }
    )

  private def safeJson[T](fun: T => Json) = (value: T) =>
    Option(value) match {
      case Some(realValue) => fun(realValue)
      case None            => Null
    }

  // TODO: make encoder aware of NU Types to encode things like multiset differently. Right now its handled by calling
  //  toString on keys.
  private def encodeMap(map: Map[_, _]) = {
    val mapWithStringKeys = map.view.map { case (k, v) =>
      k.toString -> encode(v)
    }

    fromFields(mapWithStringKeys)
  }

}
