package pl.touk.nussknacker.engine.api.deployment

import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.deployment.DeploymentData

import scala.concurrent.{ExecutionContext, Future}

trait ProcessingTypeDeploymentService {

  def getDeployedScenarios(implicit ec: ExecutionContext): Future[List[DeployedScenarioData]]

}

case class DeployedScenarioData(processVersion: ProcessVersion, deploymentData: DeploymentData, resolvedScenario: CanonicalProcess)

class ProcessingTypeDeploymentServiceStub(deployedScenarios: List[DeployedScenarioData]) extends ProcessingTypeDeploymentService {

  override def getDeployedScenarios(implicit ec: ExecutionContext): Future[List[DeployedScenarioData]] = Future.successful(deployedScenarios)

}