package pl.touk.nussknacker.engine.deployment

import io.circe.generic.JsonCodec

//used for audit
@JsonCodec case class User(id: String, name: String)
