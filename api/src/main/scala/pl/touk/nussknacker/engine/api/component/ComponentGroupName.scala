package pl.touk.nussknacker.engine.api.component
import io.circe.{Decoder, Encoder}

case class ComponentGroupName(value: String) {
  def toLowerCase: String = value.toLowerCase
}

object ComponentGroupName {
  implicit val encoder: Encoder[ComponentGroupName] = Encoder.encodeString.contramap(_.value)
  implicit val decoder: Decoder[ComponentGroupName] = Decoder.decodeString.map(ComponentGroupName(_))
}
