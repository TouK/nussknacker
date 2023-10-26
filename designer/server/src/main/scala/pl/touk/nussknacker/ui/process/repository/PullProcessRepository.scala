package pl.touk.nussknacker.ui.process.repository

import pl.touk.nussknacker.engine.api.process.{ProcessId, ProcessName, VersionId}
import pl.touk.nussknacker.ui.api.ListenerApiUser
import pl.touk.nussknacker.ui.listener.User
import pl.touk.nussknacker.ui.listener.services.{
  PullProcessRepository => ListenerPullProcessRepository,
  RepositoryScenarioWithDetails,
  ScenarioShapeFetchStrategy
}
import pl.touk.nussknacker.ui.security.api.LoggedUser

import scala.concurrent.{ExecutionContext, Future}
import scala.language.implicitConversions

class PullProcessRepository(fetchingProcessRepository: FetchingProcessRepository[Future])
    extends ListenerPullProcessRepository {

  private implicit def toLoggedUser(implicit user: User): LoggedUser =
    user.asInstanceOf[ListenerApiUser].loggedUser

  override def fetchLatestProcessDetailsForProcessId[PS: ScenarioShapeFetchStrategy](
      id: ProcessId
  )(implicit listenerUser: User, ec: ExecutionContext): Future[Option[RepositoryScenarioWithDetails[PS]]] = {
    fetchingProcessRepository.fetchLatestProcessDetailsForProcessId(id = id)
  }

  override def fetchProcessDetailsForId[PS: ScenarioShapeFetchStrategy](
      processId: ProcessId,
      versionId: VersionId
  )(implicit listenerUser: User, ec: ExecutionContext): Future[Option[RepositoryScenarioWithDetails[PS]]] = {
    fetchingProcessRepository.fetchProcessDetailsForId(processId, versionId)
  }

  override def fetchProcessDetailsForName[PS: ScenarioShapeFetchStrategy](
      processName: ProcessName,
      versionId: VersionId
  )(implicit listenerUser: User, ec: ExecutionContext): Future[Option[RepositoryScenarioWithDetails[PS]]] = for {
    maybeProcessId <- fetchingProcessRepository.fetchProcessId(processName)
    processId <- maybeProcessId.fold(
      Future.failed[ProcessId](new IllegalArgumentException(s"ProcessId for $processName not found"))
    )(Future.successful)
    processDetails <- fetchLatestProcessDetailsForProcessId[PS](processId)
  } yield processDetails

}
