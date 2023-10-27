package pl.touk.nussknacker.ui.process.repository

import cats.Monad
import pl.touk.nussknacker.engine.api.process.{ProcessId, ProcessName, VersionId}
import pl.touk.nussknacker.restmodel.process.ProcessingType
import pl.touk.nussknacker.ui.db.entity.ProcessEntityData
import pl.touk.nussknacker.ui.listener.services.{RepositoryScenarioWithDetails, ScenarioShapeFetchStrategy}
import pl.touk.nussknacker.ui.process.ScenarioQuery
import pl.touk.nussknacker.ui.security.api.LoggedUser

import scala.concurrent.ExecutionContext
import scala.language.higherKinds

abstract class FetchingProcessRepository[F[_]: Monad] extends ProcessDBQueryRepository[F] {

  def fetchLatestProcessDetailsForProcessId[PS: ScenarioShapeFetchStrategy](
      id: ProcessId
  )(implicit loggedUser: LoggedUser, ec: ExecutionContext): F[Option[RepositoryScenarioWithDetails[PS]]]

  def fetchProcessDetailsForId[PS: ScenarioShapeFetchStrategy](processId: ProcessId, versionId: VersionId)(
      implicit loggedUser: LoggedUser,
      ec: ExecutionContext
  ): F[Option[RepositoryScenarioWithDetails[PS]]]

  def fetchProcessesDetails[PS: ScenarioShapeFetchStrategy](
      query: ScenarioQuery
  )(implicit loggedUser: LoggedUser, ec: ExecutionContext): F[List[RepositoryScenarioWithDetails[PS]]]

  def fetchProcessId(processName: ProcessName)(implicit ec: ExecutionContext): F[Option[ProcessId]]

  def fetchProcessName(processId: ProcessId)(implicit ec: ExecutionContext): F[Option[ProcessName]]

  // TODO: It should return F[Option[ProcessingType]]
  def fetchProcessingType(
      processId: ProcessId
  )(implicit loggedUser: LoggedUser, ec: ExecutionContext): F[ProcessingType]

  def fetchProcessDetails(processName: ProcessName)(implicit ec: ExecutionContext): F[Option[ProcessEntityData]]

}
