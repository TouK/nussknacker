package pl.touk.nussknacker.engine.api.deployment

import io.circe.{Decoder, Encoder}

final case class DeploymentId(value: String) extends AnyVal

object DeploymentId {
  implicit val encoder: Encoder[DeploymentId] = Encoder.encodeString.contramap(_.value)
  implicit val decoder: Decoder[DeploymentId] = Decoder.decodeString.map(DeploymentId(_))
}