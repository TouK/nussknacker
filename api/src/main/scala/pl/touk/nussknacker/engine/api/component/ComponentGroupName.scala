package pl.touk.nussknacker.engine.api.component
import io.circe.generic.extras.semiauto.{deriveUnwrappedDecoder, deriveUnwrappedEncoder}
import io.circe.{Decoder, Encoder}

final case class ComponentGroupName(value: String) {
  def toLowerCase: String = value.toLowerCase
}

object ComponentGroupName {
  implicit val encoder: Encoder[ComponentGroupName] = deriveUnwrappedEncoder
  implicit val decoder: Decoder[ComponentGroupName] = deriveUnwrappedDecoder
}
