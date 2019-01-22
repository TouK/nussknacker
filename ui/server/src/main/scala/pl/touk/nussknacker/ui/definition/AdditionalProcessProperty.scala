package pl.touk.nussknacker.ui.definition

import argonaut.CodecJson

case class AdditionalProcessProperty(label: String, `type`: PropertyType.Value, default: Option[String], isRequired: Boolean, values: Option[List[String]])

object AdditionalProcessProperty {
  import argonaut.Argonaut._
  implicit val propertyTypeCodec: CodecJson[PropertyType.Value] =
    CodecJson[PropertyType.Value](v => jString(v.toString), h => h.as[String].map(PropertyType.withName))
  implicit val jsonCodec: CodecJson[AdditionalProcessProperty] = CodecJson.derive[AdditionalProcessProperty]
}

object PropertyType extends Enumeration {
  type PropertyType = Value
  val select, text, string, integer = Value
}

