package pl.touk.nussknacker.ui.process.repository

import cats.Monad
import pl.touk.nussknacker.engine.api.process.{ProcessId, ProcessName, VersionId}
import pl.touk.nussknacker.restmodel.process.ProcessingType
import pl.touk.nussknacker.restmodel.processdetails.{BaseProcessDetails, ProcessShapeFetchStrategy}
import pl.touk.nussknacker.ui.db.entity.ProcessEntityData
import pl.touk.nussknacker.ui.process.ProcessesQuery
import pl.touk.nussknacker.ui.security.api.LoggedUser

import scala.concurrent.ExecutionContext
import scala.language.higherKinds

abstract class FetchingProcessRepository[F[_]: Monad] extends ProcessDBQueryRepository[F] {

  def fetchLatestProcessDetailsForProcessId[PS: ProcessShapeFetchStrategy](
      id: ProcessId
  )(implicit loggedUser: LoggedUser, ec: ExecutionContext): F[Option[BaseProcessDetails[PS]]]

  def fetchProcessDetailsForId[PS: ProcessShapeFetchStrategy](processId: ProcessId, versionId: VersionId)(
      implicit loggedUser: LoggedUser,
      ec: ExecutionContext
  ): F[Option[BaseProcessDetails[PS]]]

  def fetchProcessesDetails[PS: ProcessShapeFetchStrategy](
      query: ProcessesQuery
  )(implicit loggedUser: LoggedUser, ec: ExecutionContext): F[List[BaseProcessDetails[PS]]]

  def fetchProcessId(processName: ProcessName)(implicit ec: ExecutionContext): F[Option[ProcessId]]

  def fetchProcessName(processId: ProcessId)(implicit ec: ExecutionContext): F[Option[ProcessName]]

  // TODO: It should return F[Option[ProcessingType]]
  def fetchProcessingType(
      processId: ProcessId
  )(implicit loggedUser: LoggedUser, ec: ExecutionContext): F[ProcessingType]

  def fetchProcessDetails(processName: ProcessName)(implicit ec: ExecutionContext): F[Option[ProcessEntityData]]

}
