package pl.touk.nussknacker.engine.api.component

import io.circe.{Decoder, Encoder}

// ComponentType names and used as part of component identifiers (in urls and in stored component usages cache structure)
object ComponentType extends Enumeration {

  implicit val typeEncoder: Encoder[ComponentType.Value] = Encoder.encodeEnumeration(ComponentType)
  implicit val typeDecoder: Decoder[ComponentType.Value] = Decoder.decodeEnumeration(ComponentType)

  type ComponentType = Value

  val Source: Value          = Value("source")
  val Sink: Value            = Value("sink")
  val Service: Value         = Value("service")
  val CustomComponent: Value = Value("custom")
  val Fragment: Value        = Value("fragment")
  val BuiltIn: Value         = Value("builtin")

}
