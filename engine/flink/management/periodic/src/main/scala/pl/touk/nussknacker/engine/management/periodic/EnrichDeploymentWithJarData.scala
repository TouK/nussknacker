package pl.touk.nussknacker.engine.management.periodic

import pl.touk.nussknacker.engine.management.periodic.model.DeploymentWithJarData

import scala.concurrent.Future

trait EnrichDeploymentWithJarData {
  def apply(deploymentWithJarData: DeploymentWithJarData): Future[DeploymentWithJarData]
}

object EnrichDeploymentWithJarData {
  def noOp: EnrichDeploymentWithJarData = new EnrichDeploymentWithJarData {
    override def apply(deploymentWithJarData: DeploymentWithJarData): Future[DeploymentWithJarData] = {
      Future.successful(deploymentWithJarData)
    }
  }
}
