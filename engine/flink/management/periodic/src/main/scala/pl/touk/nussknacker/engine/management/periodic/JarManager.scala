package pl.touk.nussknacker.engine.management.periodic

import pl.touk.nussknacker.engine.api.ProcessVersion

import scala.concurrent.Future

private[periodic] trait JarManager {
  def prepareDeploymentWithJar(processVersion: ProcessVersion, processJson: String): Future[DeploymentWithJarData]
  def deployWithJar(deploymentWithJarData: DeploymentWithJarData): Future[Unit]
  def deleteJar(jarFileName: String): Future[Unit]
}
