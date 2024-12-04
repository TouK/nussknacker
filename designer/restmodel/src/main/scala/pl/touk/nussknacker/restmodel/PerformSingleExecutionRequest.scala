package pl.touk.nussknacker.restmodel

import io.circe.generic.JsonCodec

@JsonCodec final case class PerformSingleExecutionRequest(
    comment: Option[String] = None,
)
