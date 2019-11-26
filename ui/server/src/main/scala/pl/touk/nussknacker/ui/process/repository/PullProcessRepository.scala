package pl.touk.nussknacker.ui.process.repository

import pl.touk.nussknacker.restmodel.processdetails.BaseProcessDetails
import pl.touk.nussknacker.restmodel.process
import pl.touk.nussknacker.ui.listener.services.{PullProcessRepository => ListenerPullProcessRepository}
import pl.touk.nussknacker.ui.security.api.LoggedUser

import scala.concurrent.{ExecutionContext, Future}

class PullProcessRepository(fetchingProcessRepository: FetchingProcessRepository[Future]) extends ListenerPullProcessRepository {

  override def fetchLatestProcessDetailsForProcessId(id: process.ProcessId)
                                                    (implicit loggedUser: LoggedUser, ec: ExecutionContext): Future[Option[BaseProcessDetails[Unit]]] =
    fetchingProcessRepository.fetchLatestProcessDetailsForProcessId(id = id, businessView = false)

  override def fetchProcessDetailsForId(processId: process.ProcessId, versionId: Long)
                                       (implicit loggedUser: LoggedUser, ec: ExecutionContext): Future[Option[BaseProcessDetails[Unit]]] =
    fetchingProcessRepository.fetchProcessDetailsForId(processId, versionId, businessView = false)

  override def fetchProcesses(isSubprocess: Option[Boolean], isArchived: Option[Boolean],isDeployed: Option[Boolean], categories: Option[Seq[String]], processingTypes: Option[Seq[String]])
                                                   (implicit loggedUser: LoggedUser, ec: ExecutionContext): Future[List[BaseProcessDetails[Unit]]] =
    fetchingProcessRepository.fetchProcesses(
      isSubprocess = isSubprocess,
      isArchived = isArchived,
      isDeployed = isDeployed,
      categories = categories,
      processingTypes = processingTypes)
}
