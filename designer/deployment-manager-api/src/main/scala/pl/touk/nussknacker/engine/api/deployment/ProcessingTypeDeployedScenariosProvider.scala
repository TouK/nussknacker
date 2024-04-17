package pl.touk.nussknacker.engine.api.deployment

import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.deployment.DeploymentData

import scala.concurrent.{ExecutionContext, Future}

trait ProcessingTypeDeployedScenariosProvider {

  def getDeployedScenarios(implicit ec: ExecutionContext): Future[List[DeployedScenarioData]]

}

final case class DeployedScenarioData(
    processVersion: ProcessVersion,
    deploymentData: DeploymentData,
    resolvedScenario: CanonicalProcess
)

// This stub is in API module because we don't want to extract deployment-manager-tests-utils module
class ProcessingTypeDeployedScenariosProviderStub(deployedScenarios: List[DeployedScenarioData])
    extends ProcessingTypeDeployedScenariosProvider {
  override def getDeployedScenarios(implicit ec: ExecutionContext): Future[List[DeployedScenarioData]] =
    Future.successful(deployedScenarios)

}
