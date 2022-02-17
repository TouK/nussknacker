package pl.touk.nussknacker.engine.api.process

import io.circe.{Decoder, Encoder}

final case class ProcessName(value: String)

object ProcessName {
  implicit val encoder: Encoder[ProcessName] = Encoder.encodeString.contramap(_.value)
  implicit val decoder: Decoder[ProcessName] = Decoder.decodeString.map(ProcessName(_))
}
