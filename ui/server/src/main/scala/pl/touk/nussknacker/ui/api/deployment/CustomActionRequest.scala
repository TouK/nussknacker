package pl.touk.nussknacker.ui.api.deployment

import io.circe.generic.JsonCodec

@JsonCodec
case class CustomActionRequest(name: String, processId: Long)
