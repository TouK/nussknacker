package pl.touk.nussknacker.restmodel

import io.circe.generic.JsonCodec

@JsonCodec
case class CustomActionRequest(actionName: String,
                               params: Option[Map[String, String]] = None)
