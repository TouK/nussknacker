package pl.touk.nussknacker.engine.api

import pl.touk.nussknacker.engine.api.deployment.DeploymentData

case class JobData(metaData: MetaData, processVersion: ProcessVersion, deploymentData: DeploymentData)
