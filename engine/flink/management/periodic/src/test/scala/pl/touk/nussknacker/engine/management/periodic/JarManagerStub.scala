package pl.touk.nussknacker.engine.management.periodic

import pl.touk.nussknacker.engine.api.ProcessVersion

import scala.concurrent.Future

class JarManagerStub extends JarManager {

  var deployWithJarFuture: Future[Unit] = Future.successful(())

  override def prepareDeploymentWithJar(processVersion: ProcessVersion, processJson: String): Future[DeploymentWithJarData] = {
    Future.successful(
      DeploymentWithJarData(processVersion = processVersion, processJson = processJson, modelConfig = "", buildInfoJson = "{}", jarFileName = "")
    )
  }

  override def deployWithJar(deploymentWithJarData: DeploymentWithJarData): Future[Unit] = deployWithJarFuture

  override def deleteJar(jarFileName: String): Future[Unit] = Future.successful(())
}
