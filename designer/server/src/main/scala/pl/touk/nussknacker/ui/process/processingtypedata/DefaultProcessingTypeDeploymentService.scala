package pl.touk.nussknacker.ui.process.processingtypedata

import pl.touk.nussknacker.engine.api.deployment.{DeployedScenarioData, ProcessAction, ProcessingTypeDeploymentService}
import pl.touk.nussknacker.engine.api.process.ProcessId
import pl.touk.nussknacker.restmodel.process.ProcessingType
import pl.touk.nussknacker.ui.process.deployment.DeploymentService

import scala.concurrent.{ExecutionContext, Future}

class DefaultProcessingTypeDeploymentService(processingType: ProcessingType, deploymentService: DeploymentService) extends ProcessingTypeDeploymentService {
  override def getDeployedScenarios(implicit ec: ExecutionContext): Future[List[DeployedScenarioData]] =
    deploymentService.getDeployedScenarios(processingType)

  override def markProcessFinishedIfLastActionDeploy(processId: ProcessId)(implicit ec: ExecutionContext): Future[Option[ProcessAction]] =
    deploymentService.markProcessFinishedIfLastActionDeploy(processId: ProcessId)
}
