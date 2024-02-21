package pl.touk.nussknacker.engine.management.periodic

import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.deployment.{DeploymentData, ExternalDeploymentId}
import pl.touk.nussknacker.engine.management.periodic.model.DeploymentWithJarData

import scala.concurrent.Future

private[periodic] trait JarManager {

  def prepareDeploymentWithJar(
      processVersion: ProcessVersion,
  ): Future[DeploymentWithJarData]

  def deployWithJar(
      deploymentWithJarData: DeploymentWithJarData,
      deploymentData: DeploymentData,
      canonicalProcess: CanonicalProcess
  ): Future[Option[ExternalDeploymentId]]

  def deleteJar(jarFileName: String): Future[Unit]
}
