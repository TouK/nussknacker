package pl.touk.nussknacker.ui.listener.services

import pl.touk.nussknacker.engine.api.deployment.User
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.api.process.ProcessId
import pl.touk.nussknacker.restmodel.processdetails.{BaseProcessDetails, ProcessShapeFetchStrategy}


import scala.concurrent.{ExecutionContext, Future}

trait PullProcessRepository {
  def fetchLatestProcessDetailsForProcessId[PS: ProcessShapeFetchStrategy](id: ProcessId)
                                           (implicit user: User, ec: ExecutionContext): Future[Option[BaseProcessDetails[PS]]]

  def fetchProcessDetailsForId[PS: ProcessShapeFetchStrategy](processId: ProcessId, versionId: Long)
                              (implicit user: User, ec: ExecutionContext): Future[Option[BaseProcessDetails[PS]]]

  def fetchProcessDetailsForName[PS: ProcessShapeFetchStrategy](processName: ProcessName, versionId: Long)
                                            (implicit user: User, ec: ExecutionContext): Future[Option[BaseProcessDetails[PS]]]
}
