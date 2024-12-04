package pl.touk.nussknacker.restmodel

import io.circe.generic.JsonCodec

@JsonCodec
final case class PerformSingleExecutionResponse(isSuccess: Boolean, msg: String)
