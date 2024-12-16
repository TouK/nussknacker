package pl.touk.nussknacker.restmodel

import io.circe.generic.JsonCodec

@JsonCodec
final case class RunOutOfScheduleResponse(isSuccess: Boolean, msg: String)
