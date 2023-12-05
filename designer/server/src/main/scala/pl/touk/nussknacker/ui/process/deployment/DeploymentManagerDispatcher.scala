package pl.touk.nussknacker.ui.process.deployment

import pl.touk.nussknacker.engine.api.deployment.DeploymentManager
import pl.touk.nussknacker.engine.api.process.ProcessId
import pl.touk.nussknacker.engine.api.process.ProcessingType
import pl.touk.nussknacker.ui.process.processingtypedata.ProcessingTypeDataProvider
import pl.touk.nussknacker.ui.process.repository.FetchingProcessRepository
import pl.touk.nussknacker.ui.security.api.LoggedUser

import scala.concurrent.{ExecutionContext, Future}

class DeploymentManagerDispatcher(
    managers: ProcessingTypeDataProvider[DeploymentManager, _],
    processRepository: FetchingProcessRepository[Future]
) {

  def deploymentManagerUnsafe(
      processId: ProcessId
  )(implicit ec: ExecutionContext, user: LoggedUser): Future[DeploymentManager] = {
    processRepository.fetchProcessingType(processId).map(deploymentManagerUnsafe)
  }

  def deploymentManager(processingType: ProcessingType)(implicit user: LoggedUser): Option[DeploymentManager] = {
    managers.forType(processingType)
  }

  def deploymentManagerUnsafe(processingType: ProcessingType)(implicit user: LoggedUser): DeploymentManager = {
    managers.forTypeUnsafe(processingType)
  }

}
