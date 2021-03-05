package pl.touk.nussknacker.engine.management.periodic

import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.deployment.{DeploymentData, ExternalDeploymentId}
import pl.touk.nussknacker.engine.management.periodic.jar.{DeploymentWithJarData, JarManager}

import scala.concurrent.Future

class JarManagerStub extends JarManager {

  var deployWithJarFuture: Future[Option[ExternalDeploymentId]] = Future.successful(None)

  override def prepareDeploymentWithJar(processVersion: ProcessVersion, processJson: String): Future[DeploymentWithJarData] = {
    Future.successful(
      jar.DeploymentWithJarData(processVersion = processVersion, processJson = processJson, modelConfig = "", jarFileName = "")
    )
  }

  override def deployWithJar(deploymentWithJarData: DeploymentWithJarData, deploymentData: DeploymentData): Future[Option[ExternalDeploymentId]] = deployWithJarFuture

  override def deleteJar(jarFileName: String): Future[Unit] = Future.successful(())
}
