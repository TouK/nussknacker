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
                      deploymentService: DeploymentService,
                      processStateService: ProcessStateService) extends FailurePropagatingActor with LazyLogging {

  private var deploymentActionInProgress = Map[ProcessName, DeployInfo]()

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
    case GetProcessState(id@DeploymentActionInProgressForProcess(Deployment), user) =>
      implicit val loggedUser: LoggedUser = user
      replyWithPredefinedState(id, SimpleStateStatus.DuringDeploy)
    case GetProcessState(id@DeploymentActionInProgressForProcess(Cancel), user) =>
      implicit val loggedUser: LoggedUser = user
      replyWithPredefinedState(id, SimpleStateStatus.DuringCancel)
    case GetProcessState(id, user) =>
      implicit val loggedUser: LoggedUser = user
      reply(processStateService.getProcessState(id))
    case DeploymentActionFinished(process) =>
      deploymentActionInProgress -= process.name
    case GetAllInProgressDeploymentActions =>
      reply(Future.successful(AllInProgressDeploymentActionsResult(deploymentActionInProgress)))
  }

  private def replyWithPredefinedState(id: ProcessIdWithName, status: StateStatus)
                                      (implicit user: LoggedUser): Unit = {
    val processStatus = for {
      manager <- dispatcher.deploymentManager(id.id)
      state = manager.processStateDefinitionManager.processState(status)
    } yield state
    reply(processStatus)
  }

  private def handleDeploymentAction(id: ProcessIdWithName, action: DeploymentActionType, actionFuture: Future[_])
                                    (implicit user: LoggedUser): Unit = {
    deploymentActionInProgress += id.name -> DeployInfo(user.username, System.currentTimeMillis(), action)
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

  private object DeploymentActionInProgressForProcess {
    def unapply(idWithName: ProcessIdWithName): Option[DeploymentActionType] =
      deploymentActionInProgress.get(idWithName.name).map(_.action)
  }

}

object ManagementActor {
  def props(dispatcher: DeploymentManagerDispatcher,
            deploymentService: DeploymentService,
            processStateService: ProcessStateService): Props = {
    Props(new ManagementActor(dispatcher, deploymentService, processStateService))
  }

  private trait DeploymentAction {
    def id: ProcessIdWithName
  }

  private case class DeployProcess(id: ProcessIdWithName, user: LoggedUser, savepointPath: Option[String], deploymentComment: Option[DeploymentComment]) extends DeploymentAction

  private case class CancelProcess(id: ProcessIdWithName, user: LoggedUser, deploymentComment: Option[DeploymentComment]) extends DeploymentAction

  private case class GetProcessState(id: ProcessIdWithName, user: LoggedUser)

  private case class DeploymentActionFinished(id: ProcessIdWithName)

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
