package pl.touk.nussknacker.ui.listener.services

import pl.touk.nussknacker.restmodel.process.ProcessId
import pl.touk.nussknacker.restmodel.processdetails.BaseProcessDetails

import scala.concurrent.{ExecutionContext, Future}

trait PullProcessRepository {
  def fetchLatestProcessDetailsForProcessId(id: ProcessId)
                                           (implicit ec: ExecutionContext): Future[Option[BaseProcessDetails[Unit]]]

  def fetchProcessDetailsForId(processId: ProcessId, versionId: Long)
                              (implicit ec: ExecutionContext): Future[Option[BaseProcessDetails[Unit]]]
}
