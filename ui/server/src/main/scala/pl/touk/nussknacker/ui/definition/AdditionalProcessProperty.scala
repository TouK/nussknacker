package pl.touk.nussknacker.ui.definition

import io.circe.{Decoder, Encoder}
import io.circe.generic.JsonCodec

@JsonCodec case class AdditionalProcessProperty(label: String, `type`: PropertyType.Value, default: Option[String], isRequired: Boolean, values: Option[List[String]])

object PropertyType extends Enumeration {

  implicit val encoder: Encoder[PropertyType.Value] = Encoder.enumEncoder(PropertyType)
  implicit val decoder: Decoder[PropertyType.Value] = Decoder.enumDecoder(PropertyType)

  type PropertyType = Value
  val select, text, string, integer = Value
}

