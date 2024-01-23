package pl.touk.nussknacker.ui.process.processingtypedata

import pl.touk.nussknacker.engine.api.deployment.{
  DeployedScenarioData,
  ProcessAction,
  ProcessActionId,
  ProcessingTypeDeploymentService
}
import pl.touk.nussknacker.engine.api.process.{ProcessId, ProcessName}
import pl.touk.nussknacker.engine.api.process.ProcessingType
import pl.touk.nussknacker.ui.process.deployment.{AllDeployedScenarioService, DeploymentService}

import scala.concurrent.{ExecutionContext, Future}

class DefaultProcessingTypeDeploymentService(
    processingType: ProcessingType,
    deploymentService: DeploymentService,
    allDeploymentsService: AllDeployedScenarioService
) extends ProcessingTypeDeploymentService {
  override def getDeployedScenarios(implicit ec: ExecutionContext): Future[List[DeployedScenarioData]] =
    allDeploymentsService.getDeployedScenarios

  override def markProcessFinishedIfLastActionDeploy(processName: ProcessName)(
      implicit ec: ExecutionContext
  ): Future[Option[ProcessAction]] =
    deploymentService.markProcessFinishedIfLastActionDeploy(processingType, processName)

  override def markActionExecutionFinished(actionId: ProcessActionId)(implicit ec: ExecutionContext): Future[Boolean] =
    deploymentService.markActionExecutionFinished(processingType, actionId)

  override def getLastStateAction(processId: ProcessId)(implicit ec: ExecutionContext): Future[Option[ProcessAction]] =
    deploymentService.getLastStateAction(processingType, processId)

}
