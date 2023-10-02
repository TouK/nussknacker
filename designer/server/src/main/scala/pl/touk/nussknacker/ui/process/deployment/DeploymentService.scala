package pl.touk.nussknacker.ui.process.deployment

import pl.touk.nussknacker.engine.api.deployment.{DataFreshnessPolicy, DeployedScenarioData, ProcessAction, ProcessActionId, ProcessState}
import pl.touk.nussknacker.engine.api.process.{ProcessId, ProcessIdWithName, ProcessName}
import pl.touk.nussknacker.engine.deployment.ExternalDeploymentId
import pl.touk.nussknacker.restmodel.process.ProcessingType
import pl.touk.nussknacker.restmodel.processdetails.BaseProcessDetails
import pl.touk.nussknacker.ui.security.api.LoggedUser

import scala.concurrent.{ExecutionContext, Future}

trait DeploymentService extends ProcessStateService {

  def getDeployedScenarios(processingType: ProcessingType)
                          (implicit ec: ExecutionContext): Future[List[DeployedScenarioData]]

  // Inner Future in result allows to wait for deployment finish, while outer handles validation
  // We split deploy process that way because we want to be able to split FE logic into two phases:
  // - validations - it is quick part, the result will be displayed on deploy modal
  // - deployment on engine side - it is longer part, the result will be shown as a notification
  def deployProcessAsync(id: ProcessIdWithName, savepointPath: Option[String], comment: Option[String])
                        (implicit loggedUser: LoggedUser, ec: ExecutionContext): Future[Future[Option[ExternalDeploymentId]]]

  def cancelProcess(id: ProcessIdWithName, comment: Option[String])
                   (implicit loggedUser: LoggedUser, ec: ExecutionContext): Future[Unit]
  def invalidateInProgressActions(): Unit

  // TODO: This method is for backward compatibility. Remove it after switching all Flink jobs into mandatory deploymentId in StatusDetails
  def markProcessFinishedIfLastActionDeploy(processingType: ProcessingType, processName: ProcessName)(implicit ec: ExecutionContext): Future[Option[ProcessAction]]

  // Marks action execution finished. Returns true if update has some effect
  def markActionExecutionFinished(processingType: ProcessingType, actionId: ProcessActionId)(implicit ec: ExecutionContext): Future[Boolean]

  def getLastStateAction(processingType: ProcessingType, processId: ProcessId)
                        (implicit ec: ExecutionContext): Future[Option[ProcessAction]]

}

trait ProcessStateService {

  def getProcessState(processIdWithName: ProcessIdWithName)
                     (implicit user: LoggedUser, ec: ExecutionContext, freshnessPolicy: DataFreshnessPolicy): Future[ProcessState]

  def getProcessState(processDetails: BaseProcessDetails[_])
                     (implicit user: LoggedUser, ec: ExecutionContext, freshnessPolicy: DataFreshnessPolicy): Future[ProcessState]

}


