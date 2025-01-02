package pl.touk.nussknacker.restmodel

import io.circe.generic.JsonCodec

@JsonCodec final case class RunOffScheduleRequest(
    comment: Option[String] = None,
)
