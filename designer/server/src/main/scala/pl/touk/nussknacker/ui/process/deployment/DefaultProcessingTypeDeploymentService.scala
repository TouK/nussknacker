package pl.touk.nussknacker.ui.process.deployment

import pl.touk.nussknacker.engine.api.deployment.{
  DeployedScenarioData,
  ProcessAction,
  ProcessActionId,
  ProcessingTypeDeploymentService
}
import pl.touk.nussknacker.engine.api.process.{ProcessId, ProcessingType}

import scala.concurrent.{ExecutionContext, Future}

class DefaultProcessingTypeDeploymentService(
    processingType: ProcessingType,
    deploymentService: DeploymentService,
    allDeploymentsService: AllDeployedScenarioService
) extends ProcessingTypeDeploymentService {
  override def getDeployedScenarios(implicit ec: ExecutionContext): Future[List[DeployedScenarioData]] =
    allDeploymentsService.getDeployedScenarios

  override def markActionExecutionFinished(actionId: ProcessActionId)(implicit ec: ExecutionContext): Future[Boolean] =
    deploymentService.markActionExecutionFinished(actionId)

  override def getLastStateAction(processId: ProcessId)(implicit ec: ExecutionContext): Future[Option[ProcessAction]] =
    deploymentService.getLastStateAction(processId)

}
