package pl.touk.nussknacker.engine.util.json

import java.time.LocalDateTime

import argonaut.Argonaut._
import argonaut._
import io.circe.Encoder
import pl.touk.nussknacker.engine.api.{ArgonautCirce, Displayable}

case class BestEffortJsonEncoder(failOnUnkown: Boolean, highPriority: PartialFunction[Any, Json] = Map()) extends Codecs {

  import scala.collection.JavaConverters._

  private val safeString = safeJson[String](jString(_))
  private val safeLong = safeJson[Long](jNumber)
  private val safeInt = safeJson[Int](jNumber)
  private val safeDouble = safeJson[Double](jNumber(_))
  private val safeNumber = safeJson[Number](a => jNumber(a.doubleValue()))

  val circeEncoder: Encoder[Any] = Encoder.encodeJson.contramap(encode)

  def encode(obj: Any): io.circe.Json = ArgonautCirce.toCirce(encodeToArgonaut(obj))

  def encodeToArgonaut(obj: Any): Json = highPriority.applyOrElse(obj, (any: Any) =>
    any match {
      case null => jNull
      case Some(a) => encodeToArgonaut(a)
      case None => jNull
      case s: String => safeString(s)
      case a: Long => safeLong(a)
      case a: Double => safeDouble(a)
      case a: Int => safeInt(a)
      case a: Number => safeNumber(a.doubleValue())
      case a: LocalDateTime => a.asJson
      case a: Displayable => a.display
      case a: scala.collection.Map[String@unchecked, _] => encodeMap(a)
      case a: java.util.Map[String@unchecked, _] => encodeMap(a.asScala)
      case a: Traversable[_] => jArray(a.map(encodeToArgonaut).toList)
      case a: java.util.Collection[_] => jArray(a.asScala.map(encodeToArgonaut).toList)
      case _ if !failOnUnkown => safeString(any.toString)
      case a => throw new IllegalArgumentException(s"Invalid type: ${a.getClass}")
    })

  private def safeJson[T](fun: T => Json) = (value: T) => Option(value) match {
    case Some(realValue) => fun(realValue)
    case None => jNull
  }

  private def encodeMap(map: scala.collection.Map[String, _]) = {
    jObjectFields(map.toSeq.map {
      case (k, v) => k -> encodeToArgonaut(v)
    }: _*)
  }

}