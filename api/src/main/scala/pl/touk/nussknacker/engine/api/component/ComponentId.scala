package pl.touk.nussknacker.engine.api.component

import io.circe.{Decoder, Encoder}

final case class ComponentId(value: String)

object ComponentId {
  implicit val encoder: Encoder[ComponentId] = Encoder.encodeString.contramap(_.value)
  implicit val decoder: Decoder[ComponentId] = Decoder.decodeString.map(ComponentId(_))
}
