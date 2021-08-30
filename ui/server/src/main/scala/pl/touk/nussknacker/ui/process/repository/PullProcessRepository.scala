package pl.touk.nussknacker.ui.process.repository

import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.restmodel.processdetails.{BaseProcessDetails, ProcessShapeFetchStrategy}
import pl.touk.nussknacker.restmodel.process
import pl.touk.nussknacker.restmodel.process.ProcessId
import pl.touk.nussknacker.ui.listener.services.{PullProcessRepository => ListenerPullProcessRepository}
import pl.touk.nussknacker.ui.security.api.LoggedUser

import scala.concurrent.{ExecutionContext, Future}

class PullProcessRepository(fetchingProcessRepository: FetchingProcessRepository[Future]) extends ListenerPullProcessRepository {

  override def fetchLatestProcessDetailsForProcessId[PS: ProcessShapeFetchStrategy](id: process.ProcessId)
                                                                                   (implicit loggedUser: LoggedUser, ec: ExecutionContext): Future[Option[BaseProcessDetails[PS]]] =
    fetchingProcessRepository.fetchLatestProcessDetailsForProcessId(id = id)

  override def fetchProcessDetailsForId[PS: ProcessShapeFetchStrategy](processId: process.ProcessId, versionId: Long)
                                                                      (implicit loggedUser: LoggedUser, ec: ExecutionContext): Future[Option[BaseProcessDetails[PS]]] =
    fetchingProcessRepository.fetchProcessDetailsForId(processId, versionId)

  override def fetchProcessDetailsForName[PS: ProcessShapeFetchStrategy](processName: ProcessName, versionId: Long)
                                                                        (implicit loggedUser: LoggedUser, ec: ExecutionContext): Future[Option[BaseProcessDetails[PS]]] = for {
    maybeProcessId <- fetchingProcessRepository.fetchProcessId(processName)
    processId <- maybeProcessId.fold(Future.failed[ProcessId](new IllegalArgumentException(s"ProcessId for $processName not found")))(Future.successful)
    processDetails <- fetchLatestProcessDetailsForProcessId[PS](processId)
  } yield processDetails
}
