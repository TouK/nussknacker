package pl.touk.nussknacker.restmodel

import io.circe.generic.JsonCodec

@JsonCodec
final case class CustomActionRequest(actionName: String, params: Option[Map[String, String]] = None)
