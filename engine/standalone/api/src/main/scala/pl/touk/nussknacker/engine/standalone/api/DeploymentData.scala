package pl.touk.nussknacker.engine.standalone.api

import pl.touk.nussknacker.engine.api.ProcessVersion

//TODO: now we pass process version to runtime, we don't have to use deploymentTime, can be removed
case class DeploymentData(processJson: String, deploymentTime: Long, processVersion: ProcessVersion)