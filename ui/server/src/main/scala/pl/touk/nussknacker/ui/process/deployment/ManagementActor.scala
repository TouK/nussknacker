package pl.touk.nussknacker.ui.process.deployment

import akka.actor.{ActorRefFactory, Props, Status}
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine
import pl.touk.nussknacker.engine.api.deployment.ProcessActionType.ProcessActionType
import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.api.deployment.simple.{SimpleProcessStateDefinitionManager, SimpleStateStatus}
import pl.touk.nussknacker.engine.api.process.{ProcessId, ProcessName, VersionId}
import pl.touk.nussknacker.engine.api.test.TestData
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.deployment.{User => ManagerUser}
import pl.touk.nussknacker.restmodel.process.ProcessIdWithName
import pl.touk.nussknacker.ui.EspError
import pl.touk.nussknacker.ui.api.ListenerApiUser
import pl.touk.nussknacker.ui.db.entity.ProcessActionEntityData
import pl.touk.nussknacker.ui.listener.{ProcessChangeListener, User => ListenerUser}
import pl.touk.nussknacker.ui.process.processingtypedata.ProcessingTypeDataProvider
import pl.touk.nussknacker.ui.process.repository.ProcessDBQueryRepository.ProcessNotFoundError
import pl.touk.nussknacker.ui.process.repository.{DeploymentComment, FetchingProcessRepository}
import pl.touk.nussknacker.ui.security.api.LoggedUser
import pl.touk.nussknacker.ui.util.FailurePropagatingActor

import java.time.LocalDateTime
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal
import scala.util.{Failure, Success}

object ManagementActor {
  def props(managers: ProcessingTypeDataProvider[DeploymentManager],
            processRepository: FetchingProcessRepository[Future],
            scenarioResolver: ScenarioResolver,
            deploymentService: DeploymentService)
           (implicit context: ActorRefFactory): Props = {
    Props(classOf[ManagementActor], managers, processRepository, scenarioResolver, deploymentService)
  }
}

// TODO: reduce number of passed repositories - split this actor to services that will be easier to testing
// This actor should be only responsible for:
// - protecting that there won't be more than one scenario being deployed simultaneously
// - being able to check status of asynchronous perform deploy operation
// Already extracted is only DeploymentService - see docs there, but should be extracted more classes e.g.:
// - responsible for dispatching operations logic
// - translating (ProcessState from DeploymentManager and historical context of deployment/cancel actions) to user-friendly ProcessStatus
// - subprocess resolution should be a part of kind of ResolvedProcessRepository
// - (maybe) some kind of facade spinning all this things together and not being an actor e.g. ScenarioManagementFacade
class ManagementActor(managers: ProcessingTypeDataProvider[DeploymentManager],
                      processRepository: FetchingProcessRepository[Future],
                      scenarioResolver: ScenarioResolver,
                      deploymentService: DeploymentService) extends FailurePropagatingActor with LazyLogging {

  private var beingDeployed = Map[ProcessName, DeployInfo]()

  private implicit val ec: ExecutionContext = context.dispatcher

  override def receive: PartialFunction[Any, Unit] = {
    case Deploy(process, user, savepointPath, deploymentComment) =>
      ensureNoDeploymentRunning {
        val deployRes: Future[Future[ProcessActionEntityData]] = deploymentService
          .deployProcess(process, savepointPath, deploymentComment, managers.forTypeUnsafe)(user)
        //we wait for nested Future before we consider Deployment as finished
        handleDeploymentAction(process, user, DeploymentActionType.Deployment, deploymentComment, deployRes.flatten)
        //we reply to the user without waiting for finishing deployment at DeploymentManager
        reply(deployRes)
      }
    case Snapshot(id, user, savepointDir) =>
      reply(deploymentManager(id.id)(ec, user).flatMap(_.savepoint(id.name, savepointDir)))
    case Stop(id, user, savepointDir) =>
      reply(deploymentManager(id.id)(ec, user).flatMap(_.stop(id.name, savepointDir, toManagerUser(user))))
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
        manager <- deploymentManager(id.id)
      } yield manager.processStateDefinitionManager.processState(SimpleStateStatus.DuringDeploy)
      reply(processStatus)
    case CheckStatus(id, user) =>
      reply(getProcessStatus(id)(user))
    case DeploymentActionFinished(process, user, _) =>
      implicit val listenerUser: ListenerUser = ListenerApiUser(user)
      beingDeployed -= process.name
    case Test(id, canonicalProcess, category, testData, user, encoder) =>
      ensureNoDeploymentRunning {
        implicit val loggedUser: LoggedUser = user
        val testAction = for {
          manager <- deploymentManager(id.id)
          resolvedProcess <- Future.fromTry(scenarioResolver.resolveScenario(canonicalProcess, category))
          testResult <- manager.test(id.name, resolvedProcess, testData, encoder)
        } yield testResult
        reply(testAction)
      }
    case DeploymentStatus =>
      reply(Future.successful(DeploymentStatusResponse(beingDeployed)))

    case CustomAction(actionName, id, user, params) =>
      implicit val loggedUser: LoggedUser = user
      // TODO: Currently we're treating all custom actions as deployment actions; i.e. they can't be invoked if there is some deployment in progress
      ensureNoDeploymentRunning {
        val maybeProcess = processRepository.fetchLatestProcessDetailsForProcessId[CanonicalProcess](id.id)
        val res: Future[Either[CustomActionError, CustomActionResult]] = maybeProcess.flatMap {
          case Some(process) =>
            val actionReq = engine.api.deployment.CustomActionRequest(
              name = actionName,
              processVersion = process.toEngineProcessVersion,
              user = toManagerUser(user),
              params = params)
            deploymentManager(id.id).flatMap { manager =>
              manager.customActions.find(_.name == actionName) match {
                case Some(customAction) =>
                  getProcessStatus(id).flatMap(status => {
                    if (customAction.allowedStateStatusNames.contains(status.status.name)) {
                      manager.invokeCustomAction(actionReq, process.json)
                    } else
                      Future(Left(CustomActionInvalidStatus(actionReq, status.status.name)))
                  })
                case None =>
                  Future(Left(CustomActionNonExisting(actionReq)))
              }
            }
          case None =>
            Future.failed(ProcessNotFoundError(id.id.value.toString))
        }
        reply(res)
      }
  }

  private def getProcessStatus(processIdWithName: ProcessIdWithName)(implicit user: LoggedUser): Future[ProcessState] =
    for {
      actions <- processRepository.fetchProcessActions(processIdWithName.id)
      manager <- deploymentManager(processIdWithName.id)
      state <- findJobState(manager, processIdWithName)
      _ <- deploymentService.handleFinishedProcess(processIdWithName, state)
    } yield ObsoleteStateDetector.handleObsoleteStatus(state, actions.headOption)

  private def findJobState(deploymentManager: DeploymentManager, processIdWithName: ProcessIdWithName)(implicit user: LoggedUser): Future[Option[ProcessState]] =
    deploymentManager.findJobStatus(processIdWithName.name).recover {
      case NonFatal(e) =>
        logger.warn(s"Failed to get status of ${processIdWithName}: ${e.getMessage}", e)
        Some(SimpleProcessStateDefinitionManager.processState(SimpleStateStatus.FailedToGet))
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
    deploymentManager(processId.id).flatMap(_.cancel(processId.name, toManagerUser(user)))
  }

  private def deploymentManager(processId: ProcessId)(implicit ec: ExecutionContext, user: LoggedUser): Future[DeploymentManager] = {
    processRepository.fetchProcessingType(processId).map(managers.forTypeUnsafe)
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

case class Test[T](id: ProcessIdWithName, canonicalProcess: CanonicalProcess, category: String, test: TestData, user: LoggedUser, variableEncoder: Any => T)

case class DeploymentDetails(version: VersionId, deploymentComment: Option[DeploymentComment], deployedAt: LocalDateTime, action: ProcessActionType)

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
