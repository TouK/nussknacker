package pl.touk.nussknacker.ui.process.processingtypedata

import pl.touk.nussknacker.engine.api.deployment.{DeployedScenarioData, ProcessingTypeDeploymentService}
import pl.touk.nussknacker.restmodel.process.ProcessingType
import pl.touk.nussknacker.ui.process.deployment.DeploymentServiceImpl

import scala.concurrent.{ExecutionContext, Future}

class DefaultProcessingTypeDeploymentService(processingType: ProcessingType, deploymentService: DeploymentServiceImpl) extends ProcessingTypeDeploymentService {
  override def getDeployedScenarios(implicit ec: ExecutionContext): Future[List[DeployedScenarioData]] =
    deploymentService.getDeployedScenarios(processingType)
}
