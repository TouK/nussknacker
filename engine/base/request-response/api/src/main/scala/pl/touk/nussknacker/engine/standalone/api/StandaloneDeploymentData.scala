package pl.touk.nussknacker.engine.standalone.api

import io.circe.generic.JsonCodec
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.deployment.DeploymentData

//TODO: now we pass process version to runtime, we don't have to use deploymentTime, can be removed
@JsonCodec case class StandaloneDeploymentData(processJson: String, deploymentTime: Long, processVersion: ProcessVersion, deploymentData: DeploymentData)