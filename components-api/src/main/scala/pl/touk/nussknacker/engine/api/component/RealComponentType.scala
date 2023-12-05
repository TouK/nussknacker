package pl.touk.nussknacker.engine.api.component

import io.circe.{Decoder, Encoder}

// FIXME: rename to ComponentType
object RealComponentType extends Enumeration {

  implicit val typeEncoder: Encoder[RealComponentType.Value] = Encoder.encodeEnumeration(RealComponentType)
  implicit val typeDecoder: Decoder[RealComponentType.Value] = Decoder.decodeEnumeration(RealComponentType)

  type RealComponentType = Value

  val Base: Value       = Value("base")
  val CustomNode: Value = Value("customNode")
  val Fragments: Value  = Value("fragments")
  val Source: Value     = Value("source")
  val Service: Value    = Value("service")
  val Sink: Value       = Value("sink")

}
