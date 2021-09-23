package pl.touk.nussknacker.engine.api.deployment

import io.circe.derivation.annotations.JsonCodec

//used for audit
@JsonCodec case class User(id: String, name: String)
