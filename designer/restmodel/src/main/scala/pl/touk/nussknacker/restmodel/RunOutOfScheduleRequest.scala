package pl.touk.nussknacker.restmodel

import io.circe.generic.JsonCodec

@JsonCodec final case class RunOutOfScheduleRequest(
    comment: Option[String] = None,
)
