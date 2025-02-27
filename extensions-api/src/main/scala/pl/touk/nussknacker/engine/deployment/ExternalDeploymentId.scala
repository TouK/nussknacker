package pl.touk.nussknacker.engine.deployment

import io.circe.{Decoder, Encoder}
import io.circe.generic.extras.semiauto.{deriveUnwrappedDecoder, deriveUnwrappedEncoder}

//id generated by external system - e.g. Flink JobID
final case class ExternalDeploymentId(value: String) extends AnyVal {
  override def toString: String = value
}

object ExternalDeploymentId {
  implicit val encoder: Encoder[ExternalDeploymentId] = deriveUnwrappedEncoder
  implicit val decoder: Decoder[ExternalDeploymentId] = deriveUnwrappedDecoder
}
