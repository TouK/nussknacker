package pl.touk.nussknacker.engine.api

import argonaut.{JsonBigDecimal, JsonDecimal, JsonLong, Json => AJson}
import io.circe.{JsonNumber, Json => CJson}

//TODO: remove after migration
object ArgonautCirce {

  def toCirce(json: AJson): CJson =
    json.fold(
      CJson.Null,
      CJson.fromBoolean,
      {
        case a:JsonLong => CJson.fromLong(a.value)
        case a:JsonBigDecimal => CJson.fromBigDecimal(a.value)
        case a:JsonDecimal => CJson.fromJsonNumber(JsonNumber.fromDecimalStringUnsafe(a.value))
      },
      CJson.fromString,
      a => CJson.fromValues(a.map(toCirce)),
      a => CJson.fromFields(a.toMap.mapValues(toCirce))
    )

}
