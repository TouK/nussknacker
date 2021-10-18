package pl.touk.nussknacker.ui.process.repository

import pl.touk.nussknacker.engine.api.deployment.User
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.api.process.ProcessId
import pl.touk.nussknacker.restmodel.processdetails.{BaseProcessDetails, ProcessShapeFetchStrategy}
import pl.touk.nussknacker.ui.initialization.Initialization.nussknackerUser
import pl.touk.nussknacker.ui.listener.services.{PullProcessRepository => ListenerPullProcessRepository}

import scala.concurrent.{ExecutionContext, Future}

class PullProcessRepository(fetchingProcessRepository: FetchingProcessRepository[Future]) extends ListenerPullProcessRepository {

  override def fetchLatestProcessDetailsForProcessId[PS: ProcessShapeFetchStrategy](id: ProcessId)
                                                                                   (implicit loggedUser: User, ec: ExecutionContext): Future[Option[BaseProcessDetails[PS]]] =
    fetchingProcessRepository.fetchLatestProcessDetailsForProcessId(id = id)

  override def fetchProcessDetailsForId[PS: ProcessShapeFetchStrategy](processId: ProcessId, versionId: Long)
                                                                      (implicit loggedUser: User, ec: ExecutionContext): Future[Option[BaseProcessDetails[PS]]] =
    fetchingProcessRepository.fetchProcessDetailsForId(processId, versionId)

  override def fetchProcessDetailsForName[PS: ProcessShapeFetchStrategy](processName: ProcessName, versionId: Long)
                                                                        (implicit loggedUser: User, ec: ExecutionContext): Future[Option[BaseProcessDetails[PS]]] = for {
    maybeProcessId <- fetchingProcessRepository.fetchProcessId(processName)
    processId <- maybeProcessId.fold(Future.failed[ProcessId](new IllegalArgumentException(s"ProcessId for $processName not found")))(Future.successful)
    processDetails <- fetchLatestProcessDetailsForProcessId[PS](processId)
  } yield processDetails
}
