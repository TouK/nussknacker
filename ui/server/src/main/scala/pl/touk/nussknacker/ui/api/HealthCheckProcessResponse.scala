package pl.touk.nussknacker.ui.api

import io.circe.{Decoder, Encoder}
import io.circe.derivation.annotations.JsonCodec

sealed trait HealthCheckProcessResponseStatus
case object OK extends HealthCheckProcessResponseStatus
case object ERROR extends HealthCheckProcessResponseStatus

object HealthCheckProcessResponseStatus {
  import io.circe.generic.extras.semiauto._
  implicit val encoder: Encoder[HealthCheckProcessResponseStatus] = deriveEnumerationEncoder[HealthCheckProcessResponseStatus]
  implicit val decoder: Decoder[HealthCheckProcessResponseStatus] = deriveEnumerationDecoder[HealthCheckProcessResponseStatus]
}

@JsonCodec case class HealthCheckProcessResponse(status: HealthCheckProcessResponseStatus, message: Option[String], processes: Option[Set[String]])