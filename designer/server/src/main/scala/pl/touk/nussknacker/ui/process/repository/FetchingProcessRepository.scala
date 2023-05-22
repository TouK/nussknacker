package pl.touk.nussknacker.ui.process.repository

import cats.Monad
import pl.touk.nussknacker.engine.api.deployment.ProcessAction
import pl.touk.nussknacker.engine.api.process.{ProcessId, ProcessName, VersionId}
import pl.touk.nussknacker.restmodel.processdetails.{BaseProcessDetails, ProcessShapeFetchStrategy}
import pl.touk.nussknacker.ui.db.entity.ProcessEntityData
import pl.touk.nussknacker.ui.security.api.LoggedUser

import scala.concurrent.ExecutionContext
import scala.language.higherKinds
import pl.touk.nussknacker.restmodel.process.ProcessingType
import pl.touk.nussknacker.ui.process.repository.FetchingProcessRepository.FetchProcessesDetailsQuery

object FetchingProcessRepository {
  case class FetchProcessesDetailsQuery(isSubprocess: Option[Boolean] = None,
                                        isArchived: Option[Boolean] = None,
                                        isDeployed: Option[Boolean] = None,
                                        categories: Option[Seq[String]] = None,
                                        processingTypes: Option[Seq[String]] = None,
                                        names: Option[Seq[ProcessName]] = None,
                                       )

  object FetchProcessesDetailsQuery {
    def unarchived: FetchProcessesDetailsQuery = FetchProcessesDetailsQuery(isArchived = Some(false))
    def unarchivedProcesses: FetchProcessesDetailsQuery = unarchived.copy(isSubprocess = Some(false))
    def unarchivedSubProcesses: FetchProcessesDetailsQuery = unarchived.copy(isSubprocess = Some(true))
    def deployed: FetchProcessesDetailsQuery = unarchivedProcesses.copy(isDeployed = Some(true))
  }
}

abstract class FetchingProcessRepository[F[_]: Monad] extends ProcessDBQueryRepository[F] {

  def fetchLatestProcessDetailsForProcessId[PS: ProcessShapeFetchStrategy](id: ProcessId)(implicit loggedUser: LoggedUser, ec: ExecutionContext): F[Option[BaseProcessDetails[PS]]]

  def fetchProcessDetailsForId[PS: ProcessShapeFetchStrategy](processId: ProcessId, versionId: VersionId)
                                                             (implicit loggedUser: LoggedUser, ec: ExecutionContext): F[Option[BaseProcessDetails[PS]]]

  def fetchProcessesDetails[PS: ProcessShapeFetchStrategy](query: FetchProcessesDetailsQuery)(implicit loggedUser: LoggedUser, ec: ExecutionContext): F[List[BaseProcessDetails[PS]]]

  def fetchProcessId(processName: ProcessName)(implicit ec: ExecutionContext): F[Option[ProcessId]]

  def fetchProcessName(processId: ProcessId)(implicit ec: ExecutionContext): F[Option[ProcessName]]

  def fetchProcessActions(processId: ProcessId)(implicit ec: ExecutionContext): F[List[ProcessAction]]

  //TODO: It should return F[Option[ProcessingType]]
  def fetchProcessingType(processId: ProcessId)(implicit loggedUser: LoggedUser, ec: ExecutionContext): F[ProcessingType]

  def fetchProcessDetails(processName: ProcessName)(implicit ec: ExecutionContext): F[Option[ProcessEntityData]]

}
