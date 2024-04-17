package pl.touk.nussknacker.engine.api

import io.circe.{Decoder, Encoder, KeyDecoder, KeyEncoder}

case class NodeId(id: String) {
  override def toString: String = id
}

object NodeId {
  implicit val encoder: Encoder[NodeId] = Encoder.encodeString.contramap(_.id)
  implicit val decoder: Decoder[NodeId] = Decoder.decodeString.map(NodeId(_))

  implicit val keyEncoder: KeyEncoder[NodeId] = KeyEncoder.encodeKeyString.contramap(_.id)
  implicit val keyDecoder: KeyDecoder[NodeId] = KeyDecoder.decodeKeyString.map(NodeId(_))
}
