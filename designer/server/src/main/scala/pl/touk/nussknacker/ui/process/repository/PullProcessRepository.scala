package pl.touk.nussknacker.ui.process.repository

import pl.touk.nussknacker.engine.api.deployment.ScenarioActivity
import pl.touk.nussknacker.engine.api.graph.ScenarioGraph
import pl.touk.nussknacker.engine.api.process.{ProcessId, ProcessName, VersionId}
import pl.touk.nussknacker.ui.api.ListenerApiUser
import pl.touk.nussknacker.ui.listener.{ListenerScenarioWithDetails, User}
import pl.touk.nussknacker.ui.listener.services.{PullProcessRepository => ListenerPullProcessRepository}
import pl.touk.nussknacker.ui.process.scenarioactivity.FetchScenarioActivityService
import pl.touk.nussknacker.ui.process.scenarioactivity.FetchScenarioActivityService.ScenarioActivityFetchError
import pl.touk.nussknacker.ui.security.api.LoggedUser

import scala.concurrent.{ExecutionContext, Future}
import scala.language.implicitConversions

class PullProcessRepository(
    fetchingProcessRepository: FetchingProcessRepository[Future],
    activityService: FetchScenarioActivityService
) extends ListenerPullProcessRepository {

  private implicit def toLoggedUser(implicit user: User): LoggedUser =
    user.asInstanceOf[ListenerApiUser].loggedUser

  override def fetchLatestProcessDetailsForProcessId(
      id: ProcessId
  )(implicit listenerUser: User, ec: ExecutionContext): Future[Option[ListenerScenarioWithDetails]] = {
    fetchingProcessRepository.fetchLatestProcessDetailsForProcessId[ScenarioGraph](id = id)
  }

  override def fetchProcessDetailsForId(
      processId: ProcessId,
      versionId: VersionId
  )(implicit listenerUser: User, ec: ExecutionContext): Future[Option[ListenerScenarioWithDetails]] = {
    fetchingProcessRepository.fetchProcessDetailsForId[ScenarioGraph](processId, versionId)
  }

  override def fetchProcessDetailsForName(
      processName: ProcessName,
      versionId: VersionId
  )(implicit listenerUser: User, ec: ExecutionContext): Future[Option[ListenerScenarioWithDetails]] = for {
    maybeProcessId <- fetchingProcessRepository.fetchProcessId(processName)
    processId <- maybeProcessId.fold(
      Future.failed[ProcessId](new IllegalArgumentException(s"Scenario with name $processName not found"))
    )(Future.successful)
    processDetails <- fetchProcessDetailsForId(processId, versionId)
  } yield processDetails

  override def fetchActivities(
      processName: ProcessName,
  )(implicit listenerUser: User, ec: ExecutionContext): Future[List[ScenarioActivity]] =
    activityService
      .fetchActivities(processName, None)
      .value
      .flatMap {
        case Right(value) => Future.successful(value)
        case Left(ScenarioActivityFetchError.NoScenario(scenarioName)) =>
          Future.failed(throw new IllegalArgumentException(s"Scenario with name $scenarioName not found"))
      }

}
