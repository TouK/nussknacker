package pl.touk.nussknacker.engine.api.process

import io.circe.{Decoder, Encoder, KeyDecoder, KeyEncoder}

final case class ProcessName(value: String) {
  override def toString: String = value
}

object ProcessName {
  implicit val encoder: Encoder[ProcessName] = Encoder.encodeString.contramap(_.value)
  implicit val decoder: Decoder[ProcessName] = Decoder.decodeString.map(ProcessName(_))

  implicit val keyEncoder: KeyEncoder[ProcessName] = KeyEncoder.encodeKeyString.contramap(_.value)
  implicit val keyDecoder: KeyDecoder[ProcessName] = KeyDecoder.decodeKeyString.map(ProcessName(_))
}
