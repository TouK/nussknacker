package pl.touk.nussknacker.ui.process.repository

import pl.touk.nussknacker.engine.ProcessingTypeData.ProcessingType
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.restmodel.process.ProcessId
import pl.touk.nussknacker.restmodel.processdetails.{DeploymentHistoryEntry, ProcessDetails}
import pl.touk.nussknacker.ui.EspError.XError
import pl.touk.nussknacker.ui.db.entity.ProcessVersionEntityData
import pl.touk.nussknacker.ui.process.repository.ProcessRepository.ProcessNotFoundError
import pl.touk.nussknacker.ui.security.api.LoggedUser

import scala.concurrent.{ExecutionContext, Future}

trait FetchingProcessRepository {

  def fetchLatestProcessDetailsForProcessId(id: ProcessId, businessView: Boolean = false)
                                           (implicit loggedUser: LoggedUser, ec: ExecutionContext): Future[Option[ProcessDetails]]

  def fetchLatestProcessDetailsForProcessIdEither(id: ProcessId, businessView: Boolean = false)
                                                 (implicit loggedUser: LoggedUser, ec: ExecutionContext): Future[XError[ProcessDetails]] = {
    fetchLatestProcessDetailsForProcessId(id).map[XError[ProcessDetails]] {
      case None => Left(ProcessNotFoundError(id.value.toString))
      case Some(p) => Right(p)
    }
  }

  def fetchProcessDetailsForId(processId: ProcessId, versionId: Long, businessView: Boolean)
                                (implicit loggedUser: LoggedUser, ec: ExecutionContext): Future[Option[ProcessDetails]]

  def fetchLatestProcessVersion(processId: ProcessId)
                               (implicit loggedUser: LoggedUser): Future[Option[ProcessVersionEntityData]]

  def fetchProcesses()(implicit loggedUser: LoggedUser, ec: ExecutionContext): Future[List[ProcessDetails]]

  def fetchProcesses(isSubprocess: Option[Boolean], isArchived: Option[Boolean], isDeployed: Option[Boolean], categories: Option[Seq[String]], processingTypes: Option[Seq[String]])
                    (implicit loggedUser: LoggedUser, ec: ExecutionContext): Future[List[ProcessDetails]]

  def fetchCustomProcesses()(implicit loggedUser: LoggedUser, ec: ExecutionContext): Future[List[ProcessDetails]]

  def fetchProcessesDetails()(implicit loggedUser: LoggedUser, ec: ExecutionContext): Future[List[ProcessDetails]]

  def fetchProcessesDetails(processNames: List[ProcessName])(implicit loggedUser: LoggedUser, ec: ExecutionContext): Future[List[ProcessDetails]]

  def fetchSubProcessesDetails()(implicit loggedUser: LoggedUser, ec: ExecutionContext): Future[List[ProcessDetails]]

  def fetchAllProcessesDetails()(implicit loggedUser: LoggedUser, ec: ExecutionContext): Future[List[ProcessDetails]]

  def fetchArchivedProcesses()(implicit loggedUser: LoggedUser, ec: ExecutionContext): Future[List[ProcessDetails]]

  def fetchProcessId(processName: ProcessName)(implicit ec: ExecutionContext): Future[Option[ProcessId]]

  def fetchProcessName(processId: ProcessId)(implicit ec: ExecutionContext): Future[Option[ProcessName]]

  def fetchDeploymentHistory(processId: ProcessId)(implicit ec: ExecutionContext): Future[List[DeploymentHistoryEntry]]

  def fetchProcessingType(processId: ProcessId)(implicit loggedUser: LoggedUser, ec: ExecutionContext): Future[ProcessingType]
}
