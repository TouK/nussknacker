package pl.touk.nussknacker.ui.process.deployment

import akka.actor.{ActorRef, Props, Status}
import akka.pattern.ask
import akka.util.Timeout
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.api.test.ScenarioTestData
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.testmode.TestProcess.TestResults
import pl.touk.nussknacker.restmodel.process.ProcessIdWithName
import pl.touk.nussknacker.ui.process.repository.DeploymentComment
import pl.touk.nussknacker.ui.security.api.LoggedUser
import pl.touk.nussknacker.ui.util.FailurePropagatingActor

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}
import ManagementActor._

class ManagementActor(dispatcher: DeploymentManagerDispatcher,
                      deploymentService: DeploymentService,
                      customActionInvokerService: CustomActionInvokerService,
                      processStateService: ProcessStateService,
                      testExecutorService: ScenarioTestExecutorService) extends FailurePropagatingActor with LazyLogging {

  private var beingDeployed = Map[ProcessName, DeployInfo]()

  private implicit val ec: ExecutionContext = context.dispatcher

  override def receive: PartialFunction[Any, Unit] = {
    case DeployProcess(process, user, savepointPath, deploymentComment) =>
      ensureNoDeploymentRunning {
        implicit val loggedUser: LoggedUser = user
        val deployRes: Future[Future[_]] = deploymentService
          .deployProcessAsync(process, savepointPath, deploymentComment)
        //we wait for nested Future before we consider Deployment as finished
        handleDeploymentAction(process, DeploymentActionType.Deployment, deployRes.flatten)
        //we reply to the user without waiting for finishing deployment at DeploymentManager
        reply(deployRes)
      }
    case CancelProcess(id, user, deploymentComment) =>
      ensureNoDeploymentRunning {
        implicit val loggedUser: LoggedUser = user
        val cancelRes = deploymentService.cancelProcess(id, deploymentComment)
        handleDeploymentAction(id, DeploymentActionType.Cancel, cancelRes)
        reply(cancelRes)
      }
    // FIXME: this works bad - isBeingDeployed returns true when any action is in progress - also when cancel is in progress
    //        but we always return DuringDeploy
    case GetProcessState(id, user) if isBeingDeployed(id.name) =>
      implicit val loggedUser: LoggedUser = user
      val processStatus = for {
        manager <- dispatcher.deploymentManager(id.id)
      } yield manager.processStateDefinitionManager.processState(SimpleStateStatus.DuringDeploy)
      reply(processStatus)
    case GetProcessState(id, user) =>
      implicit val loggedUser: LoggedUser = user
      reply(processStateService.getProcessState(id))
    case DeploymentActionFinished(process) =>
      beingDeployed -= process.name
    case TestProcess(id, canonicalProcess, category, scenarioTestData, user, variableEncoder) =>
      ensureNoDeploymentRunning {
        implicit val loggedUser: LoggedUser = user
        reply(testExecutorService.testProcess(id, canonicalProcess, category, scenarioTestData, variableEncoder))
      }
    case GetAllInProgressDeploymentActions =>
      reply(Future.successful(AllInProgressDeploymentActionsResult(beingDeployed)))
    case InvokeCustomAction(actionName, id, user, params) =>
      // TODO: Currently we're treating all custom actions as deployment actions; i.e. they can't be invoked if there is some deployment in progress
      ensureNoDeploymentRunning {
        implicit val loggedUser: LoggedUser = user
        reply(customActionInvokerService.invokeCustomAction(actionName, id, params))
      }
  }

  private def handleDeploymentAction(id: ProcessIdWithName, action: DeploymentActionType, actionFuture: Future[_])
                                    (implicit user: LoggedUser): Unit = {
    beingDeployed += id.name -> DeployInfo(user.username, System.currentTimeMillis(), action)
    actionFuture.onComplete { _ =>
      self ! DeploymentActionFinished(id)
    }
  }

  private def reply(action: => Future[_]): Unit = {
    val replyTo = sender()
    action.onComplete {
      case Success(a) => replyTo ! a
      case Failure(ex) => replyTo ! Status.Failure(ex)
    }
  }

  private def isBeingDeployed(id: ProcessName) = beingDeployed.contains(id)

  //during deployment using Client.run Flink holds some data in statics and there is an exception when
  //test or verification run in parallel
  private def ensureNoDeploymentRunning(action: => Unit): Unit = {
    if (beingDeployed.nonEmpty) {
      sender() ! Status.Failure(new ProcessIsBeingDeployed(beingDeployed))
    } else {
      action
    }
  }

}

object ManagementActor {
  def props(dispatcher: DeploymentManagerDispatcher,
            deploymentService: DeploymentService,
            customActionInvokerService: CustomActionInvokerService,
            processStateService: ProcessStateService,
            testExecutorService: ScenarioTestExecutorService): Props = {
    Props(new ManagementActor(dispatcher, deploymentService, customActionInvokerService, processStateService, testExecutorService))
  }

  private trait DeploymentAction {
    def id: ProcessIdWithName
  }

  private case class DeployProcess(id: ProcessIdWithName, user: LoggedUser, savepointPath: Option[String], deploymentComment: Option[DeploymentComment]) extends DeploymentAction

  private case class CancelProcess(id: ProcessIdWithName, user: LoggedUser, deploymentComment: Option[DeploymentComment]) extends DeploymentAction

  private case class GetProcessState(id: ProcessIdWithName, user: LoggedUser)

  private case class TestProcess[T](id: ProcessIdWithName, canonicalProcess: CanonicalProcess, category: String, scenarioTestData: ScenarioTestData, user: LoggedUser, variableEncoder: Any => T)

  private case class DeploymentActionFinished(id: ProcessIdWithName)


  private case class InvokeCustomAction(actionName: String, id: ProcessIdWithName, user: LoggedUser, params: Map[String, String])

  private case object GetAllInProgressDeploymentActions

  class ActorBasedManagementService(managerActor: ActorRef,
                                    systemRequestTimeout: Timeout) extends ManagementService {

    private implicit val timeout: Timeout = systemRequestTimeout

    override def deployProcessAsync(id: ProcessIdWithName, savepointPath: Option[String], deploymentComment: Option[DeploymentComment])
                                   (implicit loggedUser: LoggedUser, ec: ExecutionContext): Future[Future[_]] = {
      (managerActor ? DeployProcess(id, loggedUser, savepointPath, deploymentComment)).mapTo[Future[_]]
    }

    override def cancelProcess(id: ProcessIdWithName, deploymentComment: Option[DeploymentComment])
                              (implicit loggedUser: LoggedUser, ec: ExecutionContext): Future[_] = {
      managerActor ? CancelProcess(id, loggedUser, deploymentComment)
    }

    override def getProcessState(id: ProcessIdWithName)
                                (implicit loggedUser: LoggedUser, ec: ExecutionContext): Future[ProcessState] = {
      (managerActor ? GetProcessState(id, loggedUser)).mapTo[ProcessState]
    }

    override def testProcess[T](id: ProcessIdWithName, canonicalProcess: CanonicalProcess, category: String, scenarioTestData: ScenarioTestData, variableEncoder: Any => T)
                               (implicit loggedUser: LoggedUser, ec: ExecutionContext): Future[TestResults[T]] = {
      (managerActor ? TestProcess[T](id, canonicalProcess, category, scenarioTestData, loggedUser, variableEncoder)).mapTo[TestResults[T@unchecked]]
    }

    override def getAllInProgressDeploymentActions: Future[AllInProgressDeploymentActionsResult] = {
      (managerActor ? GetAllInProgressDeploymentActions).mapTo[AllInProgressDeploymentActionsResult]
    }

    override def invokeCustomAction(actionName: String, id: ProcessIdWithName, params: Map[String, String])
                                   (implicit loggedUser: LoggedUser, ec: ExecutionContext): Future[Either[CustomActionError, CustomActionResult]] = {
      (managerActor ? InvokeCustomAction(actionName, id, loggedUser, params)).mapTo[Either[CustomActionError, CustomActionResult]]
    }

  }

}
