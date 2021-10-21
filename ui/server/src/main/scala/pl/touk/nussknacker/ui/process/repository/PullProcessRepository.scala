package pl.touk.nussknacker.ui.process.repository

import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.api.process.ProcessId
import pl.touk.nussknacker.restmodel.processdetails.{BaseProcessDetails, ProcessShapeFetchStrategy}
import pl.touk.nussknacker.ui.api.ListenerApiUser
import pl.touk.nussknacker.ui.listener.User
import pl.touk.nussknacker.ui.listener.services.{PullProcessRepository => ListenerPullProcessRepository}
import pl.touk.nussknacker.ui.security.api.LoggedUser

import scala.concurrent.{ExecutionContext, Future}

class PullProcessRepository(fetchingProcessRepository: FetchingProcessRepository[Future]) extends ListenerPullProcessRepository {

  override def fetchLatestProcessDetailsForProcessId[PS: ProcessShapeFetchStrategy](id: ProcessId)
                                                                                   (implicit listenerUser: User, ec: ExecutionContext): Future[Option[BaseProcessDetails[PS]]] = {
    implicit val loggedUser: LoggedUser = listenerUser.asInstanceOf[ListenerApiUser].loggedUser
    fetchingProcessRepository.fetchLatestProcessDetailsForProcessId(id = id)
  }

  override def fetchProcessDetailsForId[PS: ProcessShapeFetchStrategy](processId: ProcessId, versionId: Long)
                                                                      (implicit listenerUser: User, ec: ExecutionContext): Future[Option[BaseProcessDetails[PS]]] = {
    implicit val loggedUser: LoggedUser = listenerUser.asInstanceOf[ListenerApiUser].loggedUser
    fetchingProcessRepository.fetchProcessDetailsForId(processId, versionId)
  }

  override def fetchProcessDetailsForName[PS: ProcessShapeFetchStrategy](processName: ProcessName, versionId: Long)
                                                                        (implicit listenerUser: User, ec: ExecutionContext): Future[Option[BaseProcessDetails[PS]]] = for {
    maybeProcessId <- fetchingProcessRepository.fetchProcessId(processName)
    processId <- maybeProcessId.fold(Future.failed[ProcessId](new IllegalArgumentException(s"ProcessId for $processName not found")))(Future.successful)
    processDetails <- fetchLatestProcessDetailsForProcessId[PS](processId)
  } yield processDetails
}
