package pl.touk.nussknacker.engine.api

import io.circe.{Decoder, Encoder, KeyDecoder, KeyEncoder}

// User-visible label of a node
final case class NodeName(value: String) {
  override def toString: String = value
}

object NodeName {
  implicit val encoder: Encoder[NodeName] = Encoder.encodeString.contramap(_.value)
  implicit val decoder: Decoder[NodeName] = Decoder.decodeString.map(NodeName(_))

  implicit val keyEncoder: KeyEncoder[NodeName] = KeyEncoder.encodeKeyString.contramap(_.value)
  implicit val keyDecoder: KeyDecoder[NodeName] = KeyDecoder.decodeKeyString.map(NodeName(_))

  implicit val ordering: Ordering[NodeName] = Ordering.by[NodeName, String](_.value)
}
