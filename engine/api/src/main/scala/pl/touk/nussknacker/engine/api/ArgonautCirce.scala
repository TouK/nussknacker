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

}
