package pl.touk.nussknacker.restmodel

import io.circe.generic.JsonCodec

@JsonCodec
final case class RunOffScheduleResponse(isSuccess: Boolean, msg: String)
