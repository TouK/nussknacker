package pl.touk.nussknacker.engine.api.json.decoders

import io.circe.{Json, JsonNumber}

import scala.collection.immutable.ListMap
import scala.jdk.CollectionConverters._

object FromJsonSimpleDecoder {

  def jsonToAny(json: Json): Any = json.fold(
    jsonNull = null,
    jsonBoolean = identity[Boolean],
    jsonNumber = toNumber,
    jsonString = identity[String],
    jsonArray = _.map(jsonToAny).asJava,
    jsonObject = obj =>
      ListMap(
        obj.toIterable.toList.map { case (key, value) =>
          key -> jsonToAny(value)
        }: _*
      ).asJava
  )

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

}
