package pl.touk.nussknacker.ui.listener.services

import pl.touk.nussknacker.engine.api.deployment.ScenarioActivity
import pl.touk.nussknacker.engine.api.process.{ProcessId, ProcessName, VersionId}
import pl.touk.nussknacker.ui.listener.{ListenerScenarioWithDetails, User}

import scala.concurrent.{ExecutionContext, Future}

trait PullProcessRepository {

  def fetchLatestProcessDetailsForProcessId(
      id: ProcessId
  )(implicit listenerUser: User, ec: ExecutionContext): Future[Option[ListenerScenarioWithDetails]]

  def fetchProcessDetailsForId(processId: ProcessId, versionId: VersionId)(
      implicit listenerUser: User,
      ec: ExecutionContext
  ): Future[Option[ListenerScenarioWithDetails]]

  def fetchProcessDetailsForName(processName: ProcessName, versionId: VersionId)(
      implicit listenerUser: User,
      ec: ExecutionContext
  ): Future[Option[ListenerScenarioWithDetails]]

  def fetchActivities(processName: ProcessName)(
      implicit listenerUser: User,
      ec: ExecutionContext
  ): Future[List[ScenarioActivity]]

}
