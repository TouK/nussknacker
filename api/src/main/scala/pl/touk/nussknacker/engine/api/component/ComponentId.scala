package pl.touk.nussknacker.engine.api.component

import io.circe.generic.extras.semiauto.{deriveUnwrappedDecoder, deriveUnwrappedEncoder}
import io.circe.{Decoder, Encoder}

//Right now it's not yet clear what this id will be.
final case class ComponentId(value: String)

object ComponentId {
  implicit val encoder: Encoder[ComponentId] = deriveUnwrappedEncoder
  implicit val decoder: Decoder[ComponentId] = deriveUnwrappedDecoder

  def apply(value: String): ComponentId = ComponentId(value.toLowerCase)
}
