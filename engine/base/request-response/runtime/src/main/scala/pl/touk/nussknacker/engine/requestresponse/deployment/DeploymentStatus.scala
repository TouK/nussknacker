package pl.touk.nussknacker.engine.requestresponse.deployment

import io.circe.generic.JsonCodec
import pl.touk.nussknacker.engine.api.ProcessVersion

@JsonCodec case class DeploymentStatus(processVersion: ProcessVersion, deploymentTime: Long)
