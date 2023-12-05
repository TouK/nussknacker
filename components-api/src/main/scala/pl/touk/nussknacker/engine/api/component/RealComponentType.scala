package pl.touk.nussknacker.engine.api.component

import io.circe.{Decoder, Encoder}

// FIXME: rename to ComponentType
// ComponentType names and used as part of component identifiers (in urls and in stored component usages cache structure)
object RealComponentType extends Enumeration {

  implicit val typeEncoder: Encoder[RealComponentType.Value] = Encoder.encodeEnumeration(RealComponentType)
  implicit val typeDecoder: Decoder[RealComponentType.Value] = Decoder.decodeEnumeration(RealComponentType)

  type RealComponentType = Value

  val Source: Value          = Value("source")
  val Sink: Value            = Value("sink")
  val Service: Value         = Value("service")
  val CustomComponent: Value = Value("custom")
  val Fragment: Value        = Value("fragment")
  val BuiltIn: Value         = Value("builtin")

}
