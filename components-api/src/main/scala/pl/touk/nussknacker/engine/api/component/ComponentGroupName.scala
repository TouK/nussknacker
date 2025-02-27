package pl.touk.nussknacker.engine.api.component

import io.circe.{Decoder, Encoder}
import io.circe.generic.extras.semiauto.{deriveUnwrappedDecoder, deriveUnwrappedEncoder}

final case class ComponentGroupName(value: String) {
  def toLowerCase: String       = value.toLowerCase
  override def toString: String = value
}

object ComponentGroupName {
  implicit val encoder: Encoder[ComponentGroupName] = deriveUnwrappedEncoder
  implicit val decoder: Decoder[ComponentGroupName] = deriveUnwrappedDecoder
}
