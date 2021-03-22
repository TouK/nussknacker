package pl.touk.nussknacker.ui.api.deployment

import io.circe.generic.JsonCodec

@JsonCodec
case class CustomActionRequest(actionName: String,
                               params: Option[Map[String, String]] = None)
