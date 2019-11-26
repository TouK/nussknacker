package pl.touk.nussknacker.ui.listener.services

import pl.touk.nussknacker.restmodel.process.ProcessId
import pl.touk.nussknacker.restmodel.processdetails.BaseProcessDetails
import pl.touk.nussknacker.ui.security.api.LoggedUser

import scala.concurrent.{ExecutionContext, Future}

trait PullProcessRepository {
  def fetchLatestProcessDetailsForProcessId(id: ProcessId)
                                           (implicit loggedUser: LoggedUser, ec: ExecutionContext): Future[Option[BaseProcessDetails[Unit]]]

  def fetchProcessDetailsForId(processId: ProcessId, versionId: Long)
                              (implicit loggedUser: LoggedUser, ec: ExecutionContext): Future[Option[BaseProcessDetails[Unit]]]

  def fetchProcesses(isSubprocess: Option[Boolean], isArchived: Option[Boolean],isDeployed: Option[Boolean], categories: Option[Seq[String]], processingTypes: Option[Seq[String]])
                    (implicit loggedUser: LoggedUser, ec: ExecutionContext): Future[List[BaseProcessDetails[Unit]]]
}
