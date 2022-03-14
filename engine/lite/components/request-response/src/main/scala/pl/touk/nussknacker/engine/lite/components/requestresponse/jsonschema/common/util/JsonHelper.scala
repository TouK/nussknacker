package pl.touk.nussknacker.engine.lite.components.requestresponse.jsonschema.common.util

import io.circe._


object JsonHelper {

  def asObject(json: Json, rootName: String): JsonObject = {
    import Json._
    import io.circe.JsonObject._

    json.fold(
      jsonNull   = empty,
      jsonBoolean  = v => (rootName -> fromBoolean(v)) +: empty,
      jsonNumber = v => (rootName -> fromJsonNumber(v)) +: empty,
      jsonArray  = v => (rootName -> fromValues(v)) +: empty,
      jsonObject = v => v,
      jsonString = v => (rootName -> fromString(v)) +: empty
    )
  }

  def decodeMap(json: JsonObject): Map[String, Any] =
    json.keys.zip(json.values.map(decode)).toMap

  def decodeList(list: Vector[Json]): List[Any] =
    list.map(decode).toList

  def decodeNumber(number: JsonNumber): Any = {
    number.toInt.orElse(number.toLong).orElse(number.toBigInt).getOrElse{
      val doubleValue = number.toDouble
      val approx = BigDecimal.double2bigDecimal(doubleValue)
      number.toBigDecimal match {
        case Some(bigDecimal) if approx == bigDecimal => doubleValue
        case Some(bigDecimal) => bigDecimal
        case None => doubleValue
      }
    }
  }

  def decode(json: Json): Any =
    json.fold(
      null,
      b => b,
      nb => decodeNumber(nb),
      str => str,
      arr => decodeList(arr),
      obj => decodeMap(obj)
    )

  val mapDecoder: Decoder[Map[String, Any]] = (c: HCursor) => {
    c.value.asObject match {
      case Some(jsonObject) => Right(decodeMap(jsonObject))
      case None => Left(DecodingFailure("Json object expected, got: " + c.value.noSpaces, c.history))
    }
  }
}
