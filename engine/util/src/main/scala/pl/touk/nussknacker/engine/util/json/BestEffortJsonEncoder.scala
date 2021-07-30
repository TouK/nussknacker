package pl.touk.nussknacker.engine.util.json

import java.time.{Instant, LocalDate, LocalDateTime, LocalTime, OffsetDateTime, ZonedDateTime}
import java.time.format.DateTimeFormatter
import io.circe.{Encoder, Json}
import io.circe.Json._
import io.circe.java8.time
import io.circe.java8.time._
import pl.touk.nussknacker.engine.api.{ArgonautCirce, DisplayJson}
import pl.touk.nussknacker.engine.util.Implicits._
import pl.touk.nussknacker.engine.util.loader.ScalaServiceLoader

import java.util.UUID
import scala.collection.JavaConverters._

object BestEffortJsonEncoder {

  val defaultForTests: BestEffortJsonEncoder = BestEffortJsonEncoder(failOnUnkown = true, getClass.getClassLoader)

}

case class BestEffortJsonEncoder(failOnUnkown: Boolean, classLoader: ClassLoader, highPriority: PartialFunction[Any, Json] = Map()) {

  private val safeString = safeJson[String](fromString)
  private val safeLong = safeJson[Long](fromLong)
  private val safeInt = safeJson[Int](fromInt)
  private val safeDouble = safeJson[Double](fromDoubleOrNull)
  private val safeFloat = safeJson[Float](fromFloatOrNull)
  private val safeBigDecimal = safeJson[java.math.BigDecimal](a => fromBigDecimal(a))
  private val safeBigInt = safeJson[java.math.BigInteger](a => fromBigInt(a))
  private val safeNumber = safeJson[Number](a => fromDoubleOrNull(a.doubleValue())) // is it correct?

  val circeEncoder: Encoder[Any] = Encoder.encodeJson.contramap(encode)

  private val optionalEncoders = ScalaServiceLoader.load[ToJsonEncoder](classLoader).map(_.encoder(this))

  def encode(obj: Any): Json = optionalEncoders.foldLeft(highPriority)(_.orElse(_)).applyOrElse(obj, (any: Any) =>
    any match {
      case null => Null
      case Some(a) => encode(a)
      case None => Null
      case j: Json => j
      case j: argonaut.Json => ArgonautCirce.toCirce(j)
      case s: String => safeString(s)
      case a: Long => safeLong(a)
      case a: Double => safeDouble(a)
      case a: Float => safeFloat(a)
      case a: Int => safeInt(a)
      case a: java.math.BigDecimal => safeBigDecimal(a)
      case a: java.math.BigInteger => safeBigInt(a)
      case a: Number => safeNumber(a.doubleValue())
      case a: Boolean => safeJson[Boolean](fromBoolean) (a)
      case a: LocalDate => Encoder[LocalDate].apply(a)
      case a: LocalTime => Encoder[LocalTime].apply(a)
      case a: LocalDateTime => Encoder[LocalDateTime].apply(a)
      //Default implementation serializes to ISO_ZONED_DATE_TIME which is not handled well by some parsers...
      case a: ZonedDateTime => time.encodeZonedDateTimeWithFormatter(DateTimeFormatter.ISO_OFFSET_DATE_TIME).apply(a)
      case a: Instant => Encoder[Instant].apply(a)
      case a: OffsetDateTime => Encoder[OffsetDateTime].apply(a)
      case a: UUID => safeString(a.toString)
      case a: DisplayJson => a.asJson
      case a: scala.collection.Map[String@unchecked, _] => encodeMap(a.toMap)
      case a: java.util.Map[String@unchecked, _] => encodeMap(a.asScala.toMap)
      case a: Traversable[_] => fromValues(a.map(encode).toList)
      case a: Enum[_] => safeString(a.toString)
      case a: java.util.Collection[_] => fromValues(a.asScala.map(encode).toList)
      case _ if !failOnUnkown => safeString(any.toString)
      case a => throw new IllegalArgumentException(s"Invalid type: ${a.getClass}")
    })

  private def safeJson[T](fun: T => Json) = (value: T) => Option(value) match {
    case Some(realValue) => fun(realValue)
    case None => Null
  }

  private def encodeMap(map: Map[String, _]) = {
    fromFields(map.mapValuesNow(encode))
  }

}

trait ToJsonEncoder {
  def encoder(encoder: BestEffortJsonEncoder): PartialFunction[Any, Json]
}
