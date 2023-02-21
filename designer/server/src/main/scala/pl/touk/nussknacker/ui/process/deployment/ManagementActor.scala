package pl.touk.nussknacker.ui.process.deployment

import akka.actor.{ActorRefFactory, Props, Status}
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api.deployment.ProcessActionType.ProcessActionType
import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus
import pl.touk.nussknacker.engine.api.process.{ProcessName, VersionId}
import pl.touk.nussknacker.engine.api.test.ScenarioTestData
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.deployment.{User => ManagerUser}
import pl.touk.nussknacker.restmodel.process.ProcessIdWithName
import pl.touk.nussknacker.ui.EspError
import pl.touk.nussknacker.ui.db.entity.ProcessActionEntityData
import pl.touk.nussknacker.ui.process.processingtypedata.ProcessingTypeDataProvider
import pl.touk.nussknacker.ui.process.repository.{DeploymentComment, FetchingProcessRepository}
import pl.touk.nussknacker.ui.security.api.LoggedUser
import pl.touk.nussknacker.ui.util.FailurePropagatingActor

import java.time.Instant
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

object ManagementActor {
  def props(managers: ProcessingTypeDataProvider[DeploymentManager],
            processRepository: FetchingProcessRepository[Future],
            scenarioResolver: ScenarioResolver,
            deploymentService: DeploymentService)
           (implicit context: ActorRefFactory): Props = {
    val dispatcher = new DeploymentManagerDispatcher(managers, processRepository)
    val processStateService = new ProcessStateService(processRepository, dispatcher, deploymentService)
    Props(
      classOf[ManagementActor],
      dispatcher,
      scenarioResolver,
      deploymentService,
      new CustomActionInvokerService(processRepository, dispatcher, processStateService),
      processStateService)
  }
}

// TODO: reduce number of passed repositories - split this actor to services that will be easier to testing
// This actor should be only responsible for:
// - protecting that there won't be more than one scenario being deployed simultaneously
// - being able to check status of asynchronous perform deploy operation
// Already extracted is only DeploymentService - see docs there, but should be extracted more classes e.g.:
// - subprocess resolution should be a part of kind of ResolvedProcessRepository
// - (maybe) some kind of facade spinning all this things together and not being an actor e.g. ScenarioManagementFacade
class ManagementActor(dispatcher: DeploymentManagerDispatcher,
                      scenarioResolver: ScenarioResolver,
                      deploymentService: DeploymentService,
                      customActionInvokerService: CustomActionInvokerService,
                      processStateService: ProcessStateService) extends FailurePropagatingActor with LazyLogging {

  private var beingDeployed = Map[ProcessName, DeployInfo]()

  private implicit val ec: ExecutionContext = context.dispatcher

  override def receive: PartialFunction[Any, Unit] = {
    case Deploy(process, user, savepointPath, deploymentComment) =>
      ensureNoDeploymentRunning {
        val deployRes: Future[Future[ProcessActionEntityData]] = deploymentService
          .deployProcess(process, savepointPath, deploymentComment)(user)
        //we wait for nested Future before we consider Deployment as finished
        handleDeploymentAction(process, user, DeploymentActionType.Deployment, deploymentComment, deployRes.flatten)
        //we reply to the user without waiting for finishing deployment at DeploymentManager
        reply(deployRes)
      }
    case Snapshot(id, user, savepointDir) =>
      reply(dispatcher.deploymentManager(id.id)(ec, user).flatMap(_.savepoint(id.name, savepointDir)))
    case Stop(id, user, savepointDir) =>
      reply(dispatcher.deploymentManager(id.id)(ec, user).flatMap(_.stop(id.name, savepointDir, toManagerUser(user))))
    case Cancel(id, user, deploymentComment) =>
      ensureNoDeploymentRunning {
        implicit val loggedUser: LoggedUser = user
        val cancelRes = deploymentService.cancelProcess(id, deploymentComment, performCancel)
        handleDeploymentAction(id, user, DeploymentActionType.Cancel, deploymentComment, cancelRes)
        reply(cancelRes)
      }
    //TODO: should be handled in DeploymentManager
    case CheckStatus(id, user) if isBeingDeployed(id.name) =>
      implicit val loggedUser: LoggedUser = user
      val processStatus = for {
        manager <- dispatcher.deploymentManager(id.id)
      } yield manager.processStateDefinitionManager.processState(SimpleStateStatus.DuringDeploy)
      reply(processStatus)
    case CheckStatus(id, user) =>
      implicit val loggedUser: LoggedUser = user
      reply(processStateService.getProcessState(id))
    case DeploymentActionFinished(process, user, _) =>
      beingDeployed -= process.name
    case Test(id, canonicalProcess, category, scenarioTestData, user, encoder) =>
      ensureNoDeploymentRunning {
        implicit val loggedUser: LoggedUser = user
        val testAction = for {
          manager <- dispatcher.deploymentManager(id.id)
          resolvedProcess <- Future.fromTry(scenarioResolver.resolveScenario(canonicalProcess, category))
          testResult <- manager.test(id.name, resolvedProcess, scenarioTestData, encoder)
        } yield testResult
        reply(testAction)
      }
    case DeploymentStatus =>
      reply(Future.successful(DeploymentStatusResponse(beingDeployed)))

    case CustomAction(actionName, id, user, params) =>
      // TODO: Currently we're treating all custom actions as deployment actions; i.e. they can't be invoked if there is some deployment in progress
      ensureNoDeploymentRunning {
        implicit val loggedUser: LoggedUser = user
        val res = customActionInvokerService.invokeCustomAction(actionName, id, params)
        reply(res)
      }
  }

  private def handleDeploymentAction(id: ProcessIdWithName, user: LoggedUser, action: DeploymentActionType, deploymentComment: Option[DeploymentComment],
                                     actionFuture: Future[ProcessActionEntityData]): Unit = {
    beingDeployed += id.name -> DeployInfo(user.username, System.currentTimeMillis(), action)
    actionFuture.onComplete {
      case Success(details) => self ! DeploymentActionFinished(id, user, Right(DeploymentDetails(details.processVersionId, deploymentComment,details.performedAtTime, details.action)))
      case Failure(ex) => self ! DeploymentActionFinished(id, user, Left(ex))
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

  private def performCancel(processId: ProcessIdWithName)
                           (implicit user: LoggedUser) = {
    dispatcher.deploymentManager(processId.id).flatMap(_.cancel(processId.name, toManagerUser(user)))
  }

  //during deployment using Client.run Flink holds some data in statics and there is an exception when
  //test or verification run in parallel
  private def ensureNoDeploymentRunning(action: => Unit): Unit = {
    if (beingDeployed.nonEmpty) {
      sender() ! Status.Failure(new ProcessIsBeingDeployed(beingDeployed))
    } else {
      action
    }
  }

  private def toManagerUser(loggedUser: LoggedUser) = ManagerUser(loggedUser.id, loggedUser.username)

}

trait DeploymentAction {
  def id: ProcessIdWithName
}

case class Deploy(id: ProcessIdWithName, user: LoggedUser, savepointPath: Option[String], deploymentComment: Option[DeploymentComment]) extends DeploymentAction

case class Cancel(id: ProcessIdWithName, user: LoggedUser, deploymentComment: Option[DeploymentComment]) extends DeploymentAction

case class Snapshot(id: ProcessIdWithName, user: LoggedUser, savepointDir: Option[String])

case class Stop(id: ProcessIdWithName, user: LoggedUser, savepointDir: Option[String])

case class CheckStatus(id: ProcessIdWithName, user: LoggedUser)

case class Test[T](id: ProcessIdWithName, canonicalProcess: CanonicalProcess, category: String, scenarioTestData: ScenarioTestData, user: LoggedUser, variableEncoder: Any => T)

case class DeploymentDetails(version: VersionId, deploymentComment: Option[DeploymentComment], deployedAt: Instant, action: ProcessActionType)

case class DeploymentActionFinished(id: ProcessIdWithName, user: LoggedUser, failureOrDetails: Either[Throwable, DeploymentDetails])

case class DeployInfo(userId: String, time: Long, action: DeploymentActionType)

case class CustomAction(actionName: String, id: ProcessIdWithName, user: LoggedUser, params: Map[String, String])

sealed trait DeploymentActionType

object DeploymentActionType {
  case object Deployment extends DeploymentActionType
  case object Cancel extends DeploymentActionType
}

case object DeploymentStatus

case class DeploymentStatusResponse(deploymentInfo: Map[ProcessName, DeployInfo])

class ProcessIsBeingDeployed(deployments: Map[ProcessName, DeployInfo]) extends
  Exception(s"Cannot deploy/test as following deployments are in progress: ${
    deployments.map {
      case (id, info) => s"${info.action} on $id by ${info.userId}"
    }.mkString(", ")
  }") with EspError
