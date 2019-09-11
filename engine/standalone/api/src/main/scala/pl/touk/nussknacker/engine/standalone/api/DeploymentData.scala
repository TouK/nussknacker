package pl.touk.nussknacker.engine.standalone.api

import io.circe.generic.JsonCodec
import pl.touk.nussknacker.engine.api.ProcessVersion

//TODO: now we pass process version to runtime, we don't have to use deploymentTime, can be removed
@JsonCodec case class DeploymentData(processJson: String, deploymentTime: Long, processVersion: ProcessVersion)