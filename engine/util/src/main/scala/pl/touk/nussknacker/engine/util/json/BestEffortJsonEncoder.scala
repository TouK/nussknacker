package pl.touk.nussknacker.engine.util.json

import java.time.LocalDateTime

import io.circe.{Encoder, Json}
import io.circe.Json._
import io.circe.java8.time._
import pl.touk.nussknacker.engine.api.{ArgonautCirce, CirceUtil, DisplayJson}

case class BestEffortJsonEncoder(failOnUnkown: Boolean, highPriority: PartialFunction[Any, Json] = Map()) {

  import scala.collection.JavaConverters._

  private val safeString = safeJson[String](fromString)
  private val safeLong = safeJson[Long](fromLong)
  private val safeInt = safeJson[Int](fromInt)
  private val safeDouble = safeJson[Double](fromDoubleOrNull)
  private val safeNumber = safeJson[Number](a => fromDoubleOrNull(a.doubleValue()))

  val circeEncoder: Encoder[Any] = Encoder.encodeJson.contramap(encode)
  
  def encode(obj: Any): Json = highPriority.applyOrElse(obj, (any: Any) =>
    any match {
      case null => Json.Null
      case Some(a) => encode(a)
      case None => Json.Null
      case j: Json => j
      case j: argonaut.Json => ArgonautCirce.toCirce(j)
      case s: String => safeString(s)
      case a: Long => safeLong(a)
      case a: Double => safeDouble(a)
      case a: Int => safeInt(a)
      case a: Number => safeNumber(a.doubleValue())
      case a: LocalDateTime => Encoder[LocalDateTime].apply(a)
      case a: DisplayJson => a.asJson
      case a: scala.collection.Map[String@unchecked, _] => encodeMap(a)
      case a: java.util.Map[String@unchecked, _] => encodeMap(a.asScala)
      case a: Traversable[_] => fromValues(a.map(encode).toList)
      case a: java.util.Collection[_] => fromValues(a.asScala.map(encode).toList)
      case _ if !failOnUnkown => safeString(any.toString)
      case a => throw new IllegalArgumentException(s"Invalid type: ${a.getClass}")
    })

  private def safeJson[T](fun: T => Json) = (value: T) => Option(value) match {
    case Some(realValue) => fun(realValue)
    case None => Null
  }

  private def encodeMap(map: scala.collection.Map[String, _]) = {
    fromFields(map.mapValues(encode).toSeq)
  }

}