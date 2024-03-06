package pl.touk.nussknacker.restmodel

import io.circe.generic.JsonCodec

@JsonCodec
final case class CustomActionResponse(isSuccess: Boolean, msg: String)
