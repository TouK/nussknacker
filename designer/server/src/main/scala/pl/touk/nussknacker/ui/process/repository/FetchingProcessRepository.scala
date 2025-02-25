package pl.touk.nussknacker.ui.process.repository

import cats.Monad
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.ui.process.ScenarioQuery
import pl.touk.nussknacker.ui.security.api.LoggedUser

import java.sql.Timestamp
import scala.concurrent.ExecutionContext
import scala.language.higherKinds

abstract class FetchingProcessRepository[F[_]: Monad] extends ProcessDBQueryRepository[F] {

  def fetchLatestProcessDetailsForProcessId[PS: ScenarioShapeFetchStrategy](
      id: ProcessId
  )(implicit loggedUser: LoggedUser, ec: ExecutionContext): F[Option[ScenarioWithDetailsEntity[PS]]]

  def fetchProcessDetailsForId[PS: ScenarioShapeFetchStrategy](processId: ProcessId, versionId: VersionId)(
      implicit loggedUser: LoggedUser,
      ec: ExecutionContext
  ): F[Option[ScenarioWithDetailsEntity[PS]]]

  def fetchLatestProcessesDetails[PS: ScenarioShapeFetchStrategy](
      query: ScenarioQuery
  )(implicit loggedUser: LoggedUser, ec: ExecutionContext): F[List[ScenarioWithDetailsEntity[PS]]]

  def fetchLatestProcesses[PS: ScenarioShapeFetchStrategy](
      query: ScenarioQuery
  )(implicit loggedUser: LoggedUser, ec: ExecutionContext): F[List[PS]]

  def fetchLatestVersionForProcessesExcludingUsers(
      query: ScenarioQuery,
      excludedUserNames: Set[String],
  )(implicit loggedUser: LoggedUser, ec: ExecutionContext): F[Map[ProcessId, (VersionId, Timestamp, String)]]

  def getProcessVersion(
      processName: ProcessName,
      versionId: VersionId
  )(
      implicit user: LoggedUser,
  ): F[Option[ProcessVersion]]

  def fetchProcessId(processName: ProcessName)(implicit ec: ExecutionContext): F[Option[ProcessId]]

  def fetchProcessName(processId: ProcessId)(implicit ec: ExecutionContext): F[Option[ProcessName]]

  // TODO: It should return F[Option[ProcessingType]]
  def fetchProcessingType(
      processId: ProcessIdWithName
  )(implicit loggedUser: LoggedUser, ec: ExecutionContext): F[ProcessingType]

}
