package pl.touk.nussknacker.ui.listener.services

import pl.touk.nussknacker.restmodel.process.ProcessId
import pl.touk.nussknacker.restmodel.processdetails.BaseProcessDetails
import pl.touk.nussknacker.ui.security.api.LoggedUser

import scala.concurrent.ExecutionContext

trait PullProcessRepository[F[_]] {
  def fetchLatestProcessDetailsForProcessId(id: ProcessId)
                                           (implicit loggedUser: LoggedUser, ec: ExecutionContext): F[Option[BaseProcessDetails[Unit]]]

  def fetchProcessDetailsForId(processId: ProcessId, versionId: Long)
                              (implicit loggedUser: LoggedUser, ec: ExecutionContext): F[Option[BaseProcessDetails[Unit]]]
}
