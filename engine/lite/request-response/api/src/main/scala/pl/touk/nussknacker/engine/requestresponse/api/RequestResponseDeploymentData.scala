package pl.touk.nussknacker.engine.requestresponse.api

import io.circe.generic.JsonCodec
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.deployment.{DeploymentData, GraphProcess}

//TODO: now we pass process version to runtime, we don't have to use deploymentTime, can be removed
@JsonCodec case class RequestResponseDeploymentData(graphProcess: GraphProcess, deploymentTime: Long, processVersion: ProcessVersion, deploymentData: DeploymentData)
