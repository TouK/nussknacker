package pl.touk.nussknacker.engine.management.periodic

import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.deployment.{DeploymentData, ExternalDeploymentId}
import pl.touk.nussknacker.engine.management.periodic.model.DeploymentWithJarData

import scala.concurrent.Future

class JarManagerStub extends JarManager {

  var deployWithJarFuture: Future[Option[ExternalDeploymentId]]                     = Future.successful(None)
  var lastDeploymentWithJarData: Option[DeploymentWithJarData.WithCanonicalProcess] = None

  override def prepareDeploymentWithJar(
      processVersion: ProcessVersion,
      canonicalProcess: CanonicalProcess
  ): Future[DeploymentWithJarData.WithCanonicalProcess] = {
    Future.successful(
      model.DeploymentWithJarData.WithCanonicalProcess(
        processVersion = processVersion,
        jarFileName = "",
        process = canonicalProcess,
        inputConfigDuringExecutionJson = "",
      )
    )
  }

  override def deployWithJar(
      deploymentWithJarData: DeploymentWithJarData.WithCanonicalProcess,
      deploymentData: DeploymentData,
  ): Future[Option[ExternalDeploymentId]] = {
    lastDeploymentWithJarData = Some(deploymentWithJarData)
    deployWithJarFuture
  }

  override def deleteJar(jarFileName: String): Future[Unit] = Future.successful(())
}
