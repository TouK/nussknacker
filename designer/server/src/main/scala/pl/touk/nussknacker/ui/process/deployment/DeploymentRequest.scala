package pl.touk.nussknacker.ui.process.deployment

import io.circe.generic.JsonCodec

@JsonCodec case class DeploymentRequest(comment: Option[String], savepointPath: Option[String])
