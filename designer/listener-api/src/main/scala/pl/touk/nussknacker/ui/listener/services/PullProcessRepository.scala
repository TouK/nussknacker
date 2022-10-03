package pl.touk.nussknacker.ui.listener.services

import pl.touk.nussknacker.engine.api.process.{ProcessId, ProcessName, VersionId}
import pl.touk.nussknacker.restmodel.processdetails.{BaseProcessDetails, ProcessShapeFetchStrategy}
import pl.touk.nussknacker.ui.listener.User

import scala.concurrent.{ExecutionContext, Future}

trait PullProcessRepository {
  def fetchLatestProcessDetailsForProcessId[PS: ProcessShapeFetchStrategy](id: ProcessId)
                                           (implicit listenerUser: User, ec: ExecutionContext): Future[Option[BaseProcessDetails[PS]]]

  def fetchProcessDetailsForId[PS: ProcessShapeFetchStrategy](processId: ProcessId, versionId: VersionId)
                              (implicit listenerUser: User, ec: ExecutionContext): Future[Option[BaseProcessDetails[PS]]]

  def fetchProcessDetailsForName[PS: ProcessShapeFetchStrategy](processName: ProcessName, versionId: VersionId)
                                            (implicit listenerUser: User, ec: ExecutionContext): Future[Option[BaseProcessDetails[PS]]]
}

