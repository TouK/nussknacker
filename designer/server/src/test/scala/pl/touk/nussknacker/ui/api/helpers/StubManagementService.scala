package pl.touk.nussknacker.ui.api.helpers

import pl.touk.nussknacker.engine.api.deployment.ProcessState
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.restmodel.process.ProcessIdWithName
import pl.touk.nussknacker.ui.process.deployment.{AllInProgressDeploymentActionsResult, ManagementService}
import pl.touk.nussknacker.ui.process.repository.DeploymentComment
import pl.touk.nussknacker.ui.security.api.LoggedUser

import scala.concurrent.{ExecutionContext, Future}

class StubManagementService(states: Map[ProcessName, ProcessState]) extends ManagementService {
  override def getProcessState(processIdWithName: ProcessIdWithName)
                              (implicit user: LoggedUser, ec: ExecutionContext): Future[ProcessState] = Future.successful(states(processIdWithName.name))

  override def getAllInProgressDeploymentActions: Future[AllInProgressDeploymentActionsResult] = ???
  override def deployProcessAsync(id: ProcessIdWithName, savepointPath: Option[String], deploymentComment: Option[DeploymentComment])(implicit loggedUser: LoggedUser, ec: ExecutionContext): Future[Future[_]] = ???
  override def cancelProcess(id: ProcessIdWithName, deploymentComment: Option[DeploymentComment])(implicit loggedUser: LoggedUser, ec: ExecutionContext): Future[_] = ???

}
