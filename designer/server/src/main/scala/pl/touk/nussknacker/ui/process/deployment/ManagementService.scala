package pl.touk.nussknacker.ui.process.deployment

import pl.touk.nussknacker.engine.api.deployment.{CustomActionError, CustomActionResult, ProcessState}
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.api.test.ScenarioTestData
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.testmode.TestProcess.TestResults
import pl.touk.nussknacker.restmodel.process.ProcessIdWithName
import pl.touk.nussknacker.ui.process.repository.DeploymentComment
import pl.touk.nussknacker.ui.security.api.LoggedUser

import scala.concurrent.{ExecutionContext, Future}

trait ManagementService
  extends DeploymentService
    with ProcessStateService
    with InProgressDeploymentActionsProvider

trait DeploymentService {

  //inner Future in result allows to wait for deployment finish, while outer handles validation
  def deployProcessAsync(id: ProcessIdWithName, savepointPath: Option[String], deploymentComment: Option[DeploymentComment])
                        (implicit loggedUser: LoggedUser, ec: ExecutionContext): Future[Future[_]]

  def cancelProcess(id: ProcessIdWithName, deploymentComment: Option[DeploymentComment])
                   (implicit loggedUser: LoggedUser, ec: ExecutionContext): Future[_]

}

trait CustomActionInvokerService {

  def invokeCustomAction(actionName: String, id: ProcessIdWithName, params: Map[String, String])
                        (implicit loggedUser: LoggedUser, ec: ExecutionContext): Future[Either[CustomActionError, CustomActionResult]]

}

trait ProcessStateService {

  def getProcessState(processIdWithName: ProcessIdWithName)
                     (implicit user: LoggedUser, ec: ExecutionContext): Future[ProcessState]

}

trait ScenarioTestExecutorService {

  def testProcess[T](id: ProcessIdWithName, canonicalProcess: CanonicalProcess, category: String, scenarioTestData: ScenarioTestData, variableEncoder: Any => T)
                    (implicit loggedUser: LoggedUser, ec: ExecutionContext): Future[TestResults[T]]

}

trait InProgressDeploymentActionsProvider {

  def getAllInProgressDeploymentActions: Future[AllInProgressDeploymentActionsResult]

}

case class AllInProgressDeploymentActionsResult(deploymentInfo: Map[ProcessName, DeployInfo])

case class DeployInfo(userId: String, time: Long, action: DeploymentActionType)

sealed trait DeploymentActionType

object DeploymentActionType {
  case object Deployment extends DeploymentActionType
  case object Cancel extends DeploymentActionType
}