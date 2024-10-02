package pl.touk.nussknacker.ui.process.deployment

import pl.touk.nussknacker.engine.api.deployment.{DeploymentManager, ScenarioActivity}
import pl.touk.nussknacker.engine.api.process.{ProcessIdWithName, ProcessingType}
import pl.touk.nussknacker.ui.process.processingtype.provider.ProcessingTypeDataProvider
import pl.touk.nussknacker.ui.process.repository.FetchingProcessRepository
import pl.touk.nussknacker.ui.security.api.LoggedUser

import scala.concurrent.{ExecutionContext, Future}

class DeploymentManagerDispatcher(
    managers: ProcessingTypeDataProvider[DeploymentManager, _],
    processRepository: FetchingProcessRepository[Future]
) {

  def deploymentManagerUnsafe(
      processId: ProcessIdWithName
  )(implicit ec: ExecutionContext, user: LoggedUser): Future[DeploymentManager] = {
    processRepository.fetchProcessingType(processId).map(deploymentManagerUnsafe)
  }

  def deploymentManager(processingType: ProcessingType)(implicit user: LoggedUser): Option[DeploymentManager] = {
    managers.forProcessingType(processingType)
  }

  def deploymentManagerUnsafe(processingType: ProcessingType)(implicit user: LoggedUser): DeploymentManager = {
    managers.forProcessingTypeUnsafe(processingType)
  }

  def managerSpecificScenarioActivities(
      processId: ProcessIdWithName
  )(implicit ec: ExecutionContext, user: LoggedUser): Future[List[ScenarioActivity]] = {
    for {
      processingType <- processRepository.fetchProcessingType(processId)
      result <- deploymentManager(processingType) match {
        case Some(manager) => manager.managerSpecificScenarioActivities(processId)
        case None          => Future.successful(List.empty)
      }
    } yield result
  }

}
