package pl.touk.nussknacker.engine.api

import pl.touk.nussknacker.engine.api.deployment.DeploymentVersion

case class JobData(metaData: MetaData, processVersion: ProcessVersion, deploymentVersion: DeploymentVersion)
