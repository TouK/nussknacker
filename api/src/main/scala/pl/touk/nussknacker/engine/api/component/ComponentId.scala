package pl.touk.nussknacker.engine.api.component

import io.circe.generic.extras.semiauto.{deriveUnwrappedDecoder, deriveUnwrappedEncoder}
import io.circe.{Decoder, Encoder}

//Right now it's not yet clear what this id will be.
final case class ComponentId private(value: String) extends AnyVal {
  override def toString: String = value
}

object ComponentId {
  implicit val encoder: Encoder[ComponentId] = deriveUnwrappedEncoder
  implicit val decoder: Decoder[ComponentId] = deriveUnwrappedDecoder

  def apply(value: String): ComponentId = new ComponentId(value.toLowerCase)
}
