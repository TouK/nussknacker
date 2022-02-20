package pl.touk.nussknacker.engine.requestresponse.deployment

import io.circe.generic.JsonCodec
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.deployment.DeploymentData

//TODO: now we pass process version to runtime, we don't have to use deploymentTime, can be removed
@JsonCodec case class RequestResponseDeploymentData(processJson: CanonicalProcess, deploymentTime: Long, processVersion: ProcessVersion, deploymentData: DeploymentData)
