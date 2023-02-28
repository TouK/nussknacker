package pl.touk.nussknacker.ui.process.deployment

import akka.actor.{ActorRef, Props, Status}
import akka.pattern.ask
import akka.util.Timeout
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.restmodel.process.ProcessIdWithName
import pl.touk.nussknacker.ui.process.deployment.DeploymentActionType.{Cancel, Deployment}
import pl.touk.nussknacker.ui.process.deployment.ManagementActor._
import pl.touk.nussknacker.ui.process.repository.DeploymentComment
import pl.touk.nussknacker.ui.security.api.LoggedUser
import pl.touk.nussknacker.ui.util.FailurePropagatingActor

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

class ManagementActor(dispatcher: DeploymentManagerDispatcher,
                      deploymentService: DeploymentService) extends FailurePropagatingActor with LazyLogging {


  private var deploymentActionsInProgress = Map[(ProcessName, ActionId), DeployInfo]()

  private implicit val ec: ExecutionContext = context.dispatcher

  override def receive: PartialFunction[Any, Unit] = {
    case DeployProcess(process, user, savepointPath, deploymentComment) =>
      implicit val loggedUser: LoggedUser = user
      val deployRes: Future[Future[_]] = deploymentService
        .deployProcessAsync(process, savepointPath, deploymentComment)
      //we wait for nested Future before we consider Deployment as finished
      handleDeploymentAction(process, DeploymentActionType.Deployment, deployRes.flatten)
      //we reply to the user without waiting for finishing deployment at DeploymentManager
      reply(deployRes)
    case CancelProcess(id, user, deploymentComment) =>
      implicit val loggedUser: LoggedUser = user
      val cancelRes = deploymentService.cancelProcess(id, deploymentComment)
      handleDeploymentAction(id, DeploymentActionType.Cancel, cancelRes)
      reply(cancelRes)
    case GetProcessState(id, user) if deploymentActionTypesInProgress(id).contains(Deployment) =>
      implicit val loggedUser: LoggedUser = user
      replyWithPredefinedState(id, SimpleStateStatus.DuringDeploy)
    case GetProcessState(id, user) if deploymentActionTypesInProgress(id).contains(Cancel) =>
      implicit val loggedUser: LoggedUser = user
      replyWithPredefinedState(id, SimpleStateStatus.DuringCancel)
    case GetProcessState(id, user) =>
      implicit val loggedUser: LoggedUser = user
      reply(deploymentService.getProcessState(id))
    case DeploymentActionFinished(process, actionId) =>
      deploymentActionsInProgress -= ((process.name, actionId))
    case GetAllInProgressDeploymentActions =>
      val deduplicatedActions = deploymentActionsInProgress.map {
        case ((processName, _), deployInfo) => processName -> deployInfo
      }
      reply(Future.successful(AllInProgressDeploymentActionsResult(deduplicatedActions)))
  }

  private def replyWithPredefinedState(id: ProcessIdWithName, status: StateStatus)
                                      (implicit user: LoggedUser): Unit = {
    val processStatus = for {
      manager <- dispatcher.deploymentManager(id.id)
      state = manager.processStateDefinitionManager.processState(status)
    } yield state
    reply(processStatus)
  }

  private def handleDeploymentAction(id: ProcessIdWithName, actionType: DeploymentActionType, actionFuture: Future[_])
                                    (implicit user: LoggedUser): Unit = {
    val actionId = new Object
    deploymentActionsInProgress += (id.name, actionId) -> DeployInfo(user.username, System.currentTimeMillis(), actionType)
    actionFuture.onComplete { _ =>
      self ! DeploymentActionFinished(id, actionId)
    }
  }

  private def reply(action: => Future[_]): Unit = {
    val replyTo = sender()
    action.onComplete {
      case Success(a) => replyTo ! a
      case Failure(ex) => replyTo ! Status.Failure(ex)
    }
  }

  private def deploymentActionTypesInProgress(idWithName: ProcessIdWithName) = {
    deploymentActionsInProgress.toList.collect {
      case ((processName, _), DeployInfo(_, _, actionType)) if processName == idWithName.name => actionType
    }
  }

}

object ManagementActor {

  private type ActionId = AnyRef

  def props(dispatcher: DeploymentManagerDispatcher,
            deploymentService: DeploymentService): Props = {
    Props(new ManagementActor(dispatcher, deploymentService))
  }

  private trait DeploymentAction {
    def id: ProcessIdWithName
  }

  private case class DeployProcess(id: ProcessIdWithName, user: LoggedUser, savepointPath: Option[String], deploymentComment: Option[DeploymentComment]) extends DeploymentAction

  private case class CancelProcess(id: ProcessIdWithName, user: LoggedUser, deploymentComment: Option[DeploymentComment]) extends DeploymentAction

  private case class GetProcessState(id: ProcessIdWithName, user: LoggedUser)

  private case class DeploymentActionFinished(processIdWithName: ProcessIdWithName, actionId: ActionId)

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

    override def getAllInProgressDeploymentActions: Future[AllInProgressDeploymentActionsResult] = {
      (managerActor ? GetAllInProgressDeploymentActions).mapTo[AllInProgressDeploymentActionsResult]
    }

  }

}
