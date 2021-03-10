package pl.touk.nussknacker.engine.management.periodic

import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.deployment.{DeploymentData, ExternalDeploymentId}
import pl.touk.nussknacker.engine.management.periodic.model.DeploymentWithJarData

import scala.concurrent.Future

private[periodic] trait JarManager {
  def prepareDeploymentWithJar(processVersion: ProcessVersion, processJson: String): Future[DeploymentWithJarData]

  def deployWithJar(deploymentWithJarData: DeploymentWithJarData, deploymentData: DeploymentData): Future[Option[ExternalDeploymentId]]

  def deleteJar(jarFileName: String): Future[Unit]
}
