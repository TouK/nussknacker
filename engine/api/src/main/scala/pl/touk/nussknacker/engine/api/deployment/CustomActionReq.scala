package pl.touk.nussknacker.engine.api.deployment

import io.circe.generic.JsonCodec

@JsonCodec
case class CustomActionReq(name: String,
                           processId: Long)

@JsonCodec
case class CustomActionRes(msg: String)

@JsonCodec
case class CustomActionErr(msg: String)
