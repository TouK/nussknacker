package pl.touk.nussknacker.engine.api.deployment

import pl.touk.nussknacker.engine.api.ProcessVersion

import scala.concurrent.Future

trait DeploymentService {

  // shouldn't we pass also category as a filter?
  def getDeployedScenarios(processingType: String): Future[List[DeployedScenarioData]]

}

case class DeployedScenarioData(processVersion: ProcessVersion, deploymentData: DeploymentData, resolvedProcessDeploymentData: ProcessDeploymentData)

class DeploymentServiceStub(deployedScenarios: List[DeployedScenarioData]) extends DeploymentService {

  override def getDeployedScenarios(processingType: String): Future[List[DeployedScenarioData]] = Future.successful(deployedScenarios)

}