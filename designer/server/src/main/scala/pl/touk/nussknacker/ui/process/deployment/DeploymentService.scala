package pl.touk.nussknacker.ui.process.deployment

import pl.touk.nussknacker.engine.api.deployment.{DeployedScenarioData, ProcessState}
import pl.touk.nussknacker.engine.deployment.ExternalDeploymentId
import pl.touk.nussknacker.restmodel.process.{ProcessIdWithName, ProcessingType}
import pl.touk.nussknacker.restmodel.processdetails.BaseProcessDetails
import pl.touk.nussknacker.ui.process.repository.DeploymentComment
import pl.touk.nussknacker.ui.security.api.LoggedUser

import scala.concurrent.{ExecutionContext, Future}

trait DeploymentService extends ProcessStateService {

  def getDeployedScenarios(processingType: ProcessingType)
                          (implicit ec: ExecutionContext): Future[List[DeployedScenarioData]]

  // Inner Future in result allows to wait for deployment finish, while outer handles validation
  // We split deploy process that way because we want to be able to split FE logic into two phases:
  // - validations - it is quick part, the result will be displayed on deploy modal
  // - deployment on engine side - it is longer part, the result will be shown as a notification
  def deployProcessAsync(id: ProcessIdWithName, savepointPath: Option[String], deploymentComment: Option[DeploymentComment])
                        (implicit loggedUser: LoggedUser, ec: ExecutionContext): Future[Future[Option[ExternalDeploymentId]]]

  def cancelProcess(id: ProcessIdWithName, deploymentComment: Option[DeploymentComment])
                   (implicit loggedUser: LoggedUser, ec: ExecutionContext): Future[Unit]
  def invalidateInProgressActions(): Unit

}

trait ProcessStateService {

  def getProcessState(processIdWithName: ProcessIdWithName)
                     (implicit user: LoggedUser, ec: ExecutionContext): Future[ProcessState]

  // This method in contrary to getProcessState doesn't invoke target DeploymentManager - it only compute state
  // based on information available in DB
  // TODO: add caching of state returned by DeploymentManager
  def getDbProcessState(processDetails: BaseProcessDetails[_])
                       (implicit user: LoggedUser, ec: ExecutionContext): Future[ProcessState]

}


