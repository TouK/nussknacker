package pl.touk.nussknacker.engine.util.json

import java.time.{LocalDateTime, ZonedDateTime}
import java.time.format.DateTimeFormatter

import io.circe.{Encoder, Json}
import io.circe.Json._
import io.circe.java8.time
import io.circe.java8.time._
import pl.touk.nussknacker.engine.api.{ArgonautCirce, DisplayJson}
import pl.touk.nussknacker.engine.util.Implicits._
import scala.collection.JavaConverters._

import scala.util.Try

case class BestEffortJsonEncoder(failOnUnkown: Boolean, highPriority: PartialFunction[Any, Json] = Map()) {

  private val safeString = safeJson[String](fromString)
  private val safeLong = safeJson[Long](fromLong)
  private val safeInt = safeJson[Int](fromInt)
  private val safeDouble = safeJson[Double](fromDoubleOrNull)
  private val safeNumber = safeJson[Number](a => fromDoubleOrNull(a.doubleValue()))

  val circeEncoder: Encoder[Any] = Encoder.encodeJson.contramap(encode)

  private val optionalEncoders = OptionalEncoders.optionalEncoders(this)

  def encode(obj: Any): Json = highPriority.orElse(optionalEncoders).applyOrElse(obj, (any: Any) =>
    any match {
      case null => Null
      case Some(a) => encode(a)
      case None => Null
      case j: Json => j
      case j: argonaut.Json => ArgonautCirce.toCirce(j)
      case s: String => safeString(s)
      case a: Long => safeLong(a)
      case a: Double => safeDouble(a)
      case a: Int => safeInt(a)
      case a: Number => safeNumber(a.doubleValue())
      case a: Boolean => safeJson[Boolean](fromBoolean) (a)
      case a: LocalDateTime => Encoder[LocalDateTime].apply(a)
      //Default implementation serializes to ISO_ZONED_DATE_TIME which is not handled well by some parsers...
      case a: ZonedDateTime => time.encodeZonedDateTimeWithFormatter(DateTimeFormatter.ISO_OFFSET_DATE_TIME).apply(a)
      case a: DisplayJson => a.asJson
      case a: scala.collection.Map[String@unchecked, _] => encodeMap(a.toMap)
      case a: java.util.Map[String@unchecked, _] => encodeMap(a.asScala.toMap)
      case a: Traversable[_] => fromValues(a.map(encode).toList)
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

//special care should be taken when implementing optional encoders: access to classes from optional dependencies
//should be performed only after checking via hasClass that dependency is present on classpath
object OptionalEncoders {

  private def hasClass(name: String): Boolean = Try(getClass.getClassLoader.loadClass(name)).isSuccess

  private def avroEncoder(encoder: BestEffortJsonEncoder): PartialFunction[Any, Json] = if (hasClass("org.apache.avro.generic.GenericRecord")) {
    case e: org.apache.avro.generic.GenericRecord =>
      val map = e.getSchema.getFields.asScala.map(_.name()).map(n => n -> e.get(n)).toMap
      encoder.encode(map)
  } else {
    Map()
  }

  //in the future we can add other encoders
  def optionalEncoders(encoder: BestEffortJsonEncoder): PartialFunction[Any, Json] = avroEncoder(encoder)

}