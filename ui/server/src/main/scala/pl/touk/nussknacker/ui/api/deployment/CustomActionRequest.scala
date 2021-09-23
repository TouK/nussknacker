package pl.touk.nussknacker.ui.api.deployment

import io.circe.derivation.annotations.JsonCodec

@JsonCodec
case class CustomActionRequest(actionName: String,
                               params: Option[Map[String, String]] = None)
