package pl.touk.nussknacker.ui.process.repository

import pl.touk.nussknacker.restmodel.processdetails.BaseProcessDetails
import pl.touk.nussknacker.restmodel.process
import pl.touk.nussknacker.ui.security.api.LoggedUser
import pl.touk.nussknacker.ui.listener.services.{PullProcessRepository => ListenerPullProcessRepository}

import scala.concurrent.{ExecutionContext, Future}

class PullProcessRepository(fetchingProcessRepository: FetchingProcessRepository[Future]) extends ListenerPullProcessRepository[Future] {
  override def fetchLatestProcessDetailsForProcessId(id: process.ProcessId)
                                                    (implicit loggedUser: LoggedUser, ec: ExecutionContext): Future[Option[BaseProcessDetails[Unit]]] =
    fetchingProcessRepository.fetchLatestProcessDetailsForProcessId(id = id, businessView = false)

  override def fetchProcessDetailsForId(processId: process.ProcessId, versionId: Long)
                                       (implicit loggedUser: LoggedUser, ec: ExecutionContext): Future[Option[BaseProcessDetails[Unit]]] =
    fetchingProcessRepository.fetchProcessDetailsForId(processId, versionId, businessView = false)
}
