package pl.touk.nussknacker.engine.api.definition

import io.circe.{Decoder, Encoder, Json}
import io.circe.generic.JsonCodec
import pl.touk.nussknacker.engine.api.json.decoders.FromJsonSimpleDecoder.jsonToAny
import pl.touk.nussknacker.engine.api.json.encoders.ToJsonEncoder

case class MultiSelectFixedValue(value: Any, label: String) {

  val json: Json = ToJsonEncoder.default
    .encode(value)
    .valueOr(errors =>
      throw new RuntimeException(
        s"Could not encode value '$value' as JSON. Errors: ${errors.toList.mkString(", ")}"
      )
    )

}

object MultiSelectFixedValue {

  implicit val decoder: Decoder[MultiSelectFixedValue] = Decoder.instance { cursor =>
    for {
      valueJson <- cursor.downField("value").as[Json]
      label     <- cursor.downField("label").as[String]
    } yield {
      MultiSelectFixedValue(jsonToAny(valueJson), label)
    }
  }

  implicit val encoder: Encoder[MultiSelectFixedValue] = Encoder.instance { v =>
    Json.obj(
      "value" -> ToJsonEncoder.default.encodeUnsafe(v.value),
      "label" -> Json.fromString(v.label)
    )
  }

}
