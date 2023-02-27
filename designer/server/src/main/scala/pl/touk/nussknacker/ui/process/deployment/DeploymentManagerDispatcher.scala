package pl.touk.nussknacker.ui.process.deployment

import pl.touk.nussknacker.engine.api.deployment.DeploymentManager
import pl.touk.nussknacker.engine.api.process.ProcessId
import pl.touk.nussknacker.restmodel.process.ProcessingType
import pl.touk.nussknacker.ui.process.processingtypedata.ProcessingTypeDataProvider
import pl.touk.nussknacker.ui.process.repository.FetchingProcessRepository
import pl.touk.nussknacker.ui.security.api.LoggedUser

import scala.concurrent.{ExecutionContext, Future}

class DeploymentManagerDispatcher(managers: ProcessingTypeDataProvider[DeploymentManager],
                                  processRepository: FetchingProcessRepository[Future]) {

  def deploymentManager(processId: ProcessId)
                       (implicit ec: ExecutionContext, user: LoggedUser): Future[DeploymentManager] = {
    processRepository.fetchProcessingType(processId).map(managers.forTypeUnsafe)
  }

  def deploymentManager(typ: ProcessingType): DeploymentManager = {
    managers.forTypeUnsafe(typ)
  }

}
