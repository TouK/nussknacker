package pl.touk.nussknacker.engine.api

import io.circe.{Decoder, Encoder, KeyDecoder, KeyEncoder}

import java.util.UUID

// Holds a UUID-format string generated once at node creation
final case class NodeId(value: String) {
  override def toString: String = value
}

object NodeId {
  def generate(): NodeId = NodeId(UUID.randomUUID().toString)

  implicit val encoder: Encoder[NodeId] = Encoder.encodeString.contramap(_.value)
  implicit val decoder: Decoder[NodeId] = Decoder.decodeString.map(NodeId(_))

  implicit val keyEncoder: KeyEncoder[NodeId] = KeyEncoder.encodeKeyString.contramap(_.value)
  implicit val keyDecoder: KeyDecoder[NodeId] = KeyDecoder.decodeKeyString.map(NodeId(_))

  implicit val ordering: Ordering[NodeId] = Ordering.by[NodeId, String](_.value)
}
