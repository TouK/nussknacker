package pl.touk.nussknacker.engine.api.deployment

import io.circe.generic.JsonCodec

@JsonCodec
case class CustomAction(name: String)

case class CustomActionRequest(name: String,
                               processId: Long,
                               params: Map[String, String])

case class CustomActionResult(msg: String)

case class CustomActionError(msg: String)
