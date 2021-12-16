package pl.touk.nussknacker.ui.process.processingtypedata

import pl.touk.nussknacker.engine.api.deployment.{DeployedScenarioData, ProcessingTypeDeploymentService}
import pl.touk.nussknacker.restmodel.process.ProcessingType
import pl.touk.nussknacker.ui.process.deployment.DeploymentService

import scala.concurrent.Future

class DefaultProcessingTypeDeploymentService(processingType: ProcessingType, deploymentService: DeploymentService) extends ProcessingTypeDeploymentService {
  override def getDeployedScenarios: Future[List[DeployedScenarioData]] =
    deploymentService.getDeployedScenarios(processingType)
}
