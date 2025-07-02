package pl.touk.nussknacker.engine.api.json.decoders

import io.circe.{Json, JsonNumber, JsonObject}

import scala.collection.immutable.ListMap
import scala.jdk.CollectionConverters._

object FromJsonSimpleDecoder extends FromJsonSimpleDecoder

trait FromJsonSimpleDecoder {

  // FIXME abr: use
  protected val customJsonHandler: PartialFunction[Json, Any] = PartialFunction.empty

  def jsonToAny(json: Json): Any =
    json match {
      case _ if customJsonHandler.isDefinedAt(json) => customJsonHandler(json)
      case JNull()                                  => null
      case JBoolean(b)                              => b
      case JNumber(jsonNumber) =>
        toNumber(jsonNumber)
      case JString(s) => s
      case JArray(a)  => a.map(jsonToAny).asJava
      case JObject(obj) =>
        ListMap(
          obj.toIterable.toList.map { case (key, value) =>
            key -> jsonToAny(value)
          }: _*
        ).asJava
    }

  private def toNumber(jsonNumber: JsonNumber): Number = {
    // we pick the narrowest type as possible to reduce the amount of memory and computations overheads
    jsonNumber.toInt
      .map(i => i: Number)
      .orElse(jsonNumber.toLong.map(l => l: Number))
      .orElse(
        // We prefer java big decimal over float/double
        jsonNumber.toBigDecimal.map(_.bigDecimal)
      )
      .getOrElse(throw new IllegalArgumentException(s"Not supported json number: $jsonNumber"))
  }

  // We can't use Circe extractors because they are private package protected
  private object JNull {
    def unapply(json: Json): Boolean = json.asNull.isDefined
  }

  private object JBoolean {
    def unapply(json: Json): Option[Boolean] = json.asBoolean
  }

  private object JNumber {
    def unapply(json: Json): Option[JsonNumber] = json.asNumber
  }

  private object JString {
    def unapply(json: Json): Option[String] = json.asString
  }

  private object JArray {
    def unapply(json: Json): Option[Vector[Json]] = json.asArray
  }

  private object JObject {
    def unapply(json: Json): Option[JsonObject] = json.asObject
  }

}
