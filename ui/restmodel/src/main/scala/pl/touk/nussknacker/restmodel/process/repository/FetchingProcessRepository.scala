package pl.touk.nussknacker.restmodel.process.repository

import pl.touk.nussknacker.engine.ProcessingTypeData.ProcessingType
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.restmodel.db.entity.ProcessVersionEntityData
import pl.touk.nussknacker.restmodel.process.ProcessId
import pl.touk.nussknacker.restmodel.processdetails.{BaseProcessDetails, DeploymentHistoryEntry, ProcessShapeFetchStrategy}
import pl.touk.nussknacker.ui.EspError.XError
import pl.touk.nussknacker.ui.security.api.LoggedUser

import scala.concurrent.{ExecutionContext, Future}

trait FetchingProcessRepository {

  def fetchLatestProcessDetailsForProcessId[PS: ProcessShapeFetchStrategy](id: ProcessId, businessView: Boolean = false)
                                                                          (implicit loggedUser: LoggedUser, ec: ExecutionContext): Future[Option[BaseProcessDetails[PS]]]

  def fetchLatestProcessDetailsForProcessIdEither[PS: ProcessShapeFetchStrategy](id: ProcessId, businessView: Boolean = false)
                                                                                (implicit loggedUser: LoggedUser, ec: ExecutionContext): Future[XError[BaseProcessDetails[PS]]] = {
    fetchLatestProcessDetailsForProcessId(id).map[XError[BaseProcessDetails[PS]]] {
      case None => Left(ProcessNotFoundError(id.value.toString))
      case Some(p) => Right(p)
    }
  }

  def fetchProcessDetailsForId[PS: ProcessShapeFetchStrategy](processId: ProcessId, versionId: Long, businessView: Boolean)
                                                             (implicit loggedUser: LoggedUser, ec: ExecutionContext): Future[Option[BaseProcessDetails[PS]]]

  def fetchLatestProcessVersion[PS: ProcessShapeFetchStrategy](processId: ProcessId)
                                                              (implicit loggedUser: LoggedUser): Future[Option[ProcessVersionEntityData]]

  def fetchProcesses[PS: ProcessShapeFetchStrategy]()(implicit loggedUser: LoggedUser, ec: ExecutionContext): Future[List[BaseProcessDetails[PS]]]

  def fetchProcesses[PS: ProcessShapeFetchStrategy](isSubprocess: Option[Boolean], isArchived: Option[Boolean],
                                                    isDeployed: Option[Boolean], categories: Option[Seq[String]], processingTypes: Option[Seq[String]])
                                                   (implicit loggedUser: LoggedUser, ec: ExecutionContext): Future[List[BaseProcessDetails[PS]]]

  def fetchCustomProcesses[PS: ProcessShapeFetchStrategy]()(implicit loggedUser: LoggedUser, ec: ExecutionContext): Future[List[BaseProcessDetails[PS]]]

  def fetchProcessesDetails[PS: ProcessShapeFetchStrategy]()(implicit loggedUser: LoggedUser, ec: ExecutionContext): Future[List[BaseProcessDetails[PS]]]

  def fetchProcessesDetails[PS: ProcessShapeFetchStrategy](processNames: List[ProcessName])
                                                          (implicit loggedUser: LoggedUser, ec: ExecutionContext): Future[List[BaseProcessDetails[PS]]]

  def fetchSubProcessesDetails[PS: ProcessShapeFetchStrategy]()(implicit loggedUser: LoggedUser, ec: ExecutionContext): Future[List[BaseProcessDetails[PS]]]

  def fetchAllProcessesDetails[PS: ProcessShapeFetchStrategy]()(implicit loggedUser: LoggedUser, ec: ExecutionContext): Future[List[BaseProcessDetails[PS]]]

  def fetchArchivedProcesses[PS: ProcessShapeFetchStrategy]()(implicit loggedUser: LoggedUser, ec: ExecutionContext): Future[List[BaseProcessDetails[PS]]]

  def fetchProcessId(processName: ProcessName)(implicit ec: ExecutionContext): Future[Option[ProcessId]]

  def fetchProcessName(processId: ProcessId)(implicit ec: ExecutionContext): Future[Option[ProcessName]]

  def fetchDeploymentHistory(processId: ProcessId)(implicit ec: ExecutionContext): Future[List[DeploymentHistoryEntry]]

  def fetchProcessingType(processId: ProcessId)(implicit loggedUser: LoggedUser, ec: ExecutionContext): Future[ProcessingType]
}
