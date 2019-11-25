package pl.touk.nussknacker.ui.process.repository

import pl.touk.nussknacker.restmodel.processdetails.BaseProcessDetails
import pl.touk.nussknacker.restmodel.process
import pl.touk.nussknacker.ui.security.api.ReadOnlyTechUser
import pl.touk.nussknacker.ui.listener.services.{PullProcessRepository => ListenerPullProcessRepository}

import scala.concurrent.{ExecutionContext, Future}

class PullProcessRepository(fetchingProcessRepository: FetchingProcessRepository[Future]) extends ListenerPullProcessRepository {

  private implicit val user = ReadOnlyTechUser

  override def fetchLatestProcessDetailsForProcessId(id: process.ProcessId)
                                                    (implicit ec: ExecutionContext): Future[Option[BaseProcessDetails[Unit]]] =
    fetchingProcessRepository.fetchLatestProcessDetailsForProcessId(id = id, businessView = false)

  override def fetchProcessDetailsForId(processId: process.ProcessId, versionId: Long)
                                       (implicit ec: ExecutionContext): Future[Option[BaseProcessDetails[Unit]]] =
    fetchingProcessRepository.fetchProcessDetailsForId(processId, versionId, businessView = false)
}
