package pl.touk.nussknacker.engine.api

import argonaut.{DecodeJson, EncodeJson, JsonBigDecimal, JsonDecimal, JsonLong, Json => AJson}
import io.circe.{Decoder, Encoder, JsonNumber, Json => CJson}

//TODO: remove after migration
object ArgonautCirce {

  def toCirce(json: AJson): CJson =
    json.fold(
      CJson.Null,
      CJson.fromBoolean,
      {
        case a:JsonLong => CJson.fromLong(a.value)
        case a:JsonBigDecimal => CJson.fromBigDecimal(a.value)
        case a:JsonDecimal => CJson.fromJsonNumber(JsonNumber.fromIntegralStringUnsafe(a.value))
      },
      CJson.fromString,
      a => CJson.fromValues(a.map(toCirce)),
      a => CJson.fromFields(a.toMap.mapValues(toCirce))
    )

  def toArgonaut(json: CJson): AJson =
    json.fold(
      AJson.jNull,
      AJson.jBool,
      a => a.toBigDecimal.map(AJson.jNumber).getOrElse(AJson.jNumber(a.toDouble)),
      AJson.jString,
      a => AJson.jArray(a.map(toArgonaut).toList),
      a => AJson.jObjectAssocList(a.toMap.mapValues(toArgonaut).toList)
    )


  import argonaut.Argonaut._
  def toEncoder[T:EncodeJson]: Encoder[T] = Encoder.encodeJson.contramap(nd => ArgonautCirce.toCirce(nd.asJson))

  def toDecoder[T:DecodeJson]: Decoder[T] = Decoder.decodeJson.emap(nd => implicitly[DecodeJson[T]].decodeJson(ArgonautCirce.toArgonaut(nd)).toEither.left.map(_._1))

}
