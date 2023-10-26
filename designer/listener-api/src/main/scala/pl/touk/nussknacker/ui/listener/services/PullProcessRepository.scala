package pl.touk.nussknacker.ui.listener.services

import pl.touk.nussknacker.engine.api.process.{ProcessId, ProcessName, VersionId}
import pl.touk.nussknacker.ui.listener.User

import scala.concurrent.{ExecutionContext, Future}

trait PullProcessRepository {

  def fetchLatestProcessDetailsForProcessId[PS: ScenarioShapeFetchStrategy](
      id: ProcessId
  )(implicit listenerUser: User, ec: ExecutionContext): Future[Option[RepositoryScenarioWithDetails[PS]]]

  def fetchProcessDetailsForId[PS: ScenarioShapeFetchStrategy](processId: ProcessId, versionId: VersionId)(
      implicit listenerUser: User,
      ec: ExecutionContext
  ): Future[Option[RepositoryScenarioWithDetails[PS]]]

  def fetchProcessDetailsForName[PS: ScenarioShapeFetchStrategy](processName: ProcessName, versionId: VersionId)(
      implicit listenerUser: User,
      ec: ExecutionContext
  ): Future[Option[RepositoryScenarioWithDetails[PS]]]

}
