package pl.touk.nussknacker.engine.api.deployment

import io.circe.generic.JsonCodec

@JsonCodec case class DeploymentId(value: String) extends AnyVal
