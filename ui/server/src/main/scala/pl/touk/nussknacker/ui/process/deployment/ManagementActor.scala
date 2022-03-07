package pl.touk.nussknacker.ui.process.deployment

import akka.actor.{ActorRefFactory, Props, Status}
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.deployment.ProcessActionType.ProcessActionType
import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.api.deployment.simple.{SimpleProcessStateDefinitionManager, SimpleStateStatus}
import pl.touk.nussknacker.engine.api.process.{ProcessId, ProcessName, VersionId}
import pl.touk.nussknacker.engine.api.test.TestData
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.deployment.{DeploymentData, ExternalDeploymentId, User => ManagerUser}
import pl.touk.nussknacker.restmodel.process.{ProcessIdWithName, ProcessingType}
import pl.touk.nussknacker.restmodel.processdetails.ProcessAction
import pl.touk.nussknacker.ui.EspError
import pl.touk.nussknacker.ui.api.ListenerApiUser
import pl.touk.nussknacker.ui.db.entity.ProcessActionEntityData
import pl.touk.nussknacker.ui.listener.ProcessChangeEvent.{OnDeployActionFailed, OnDeployActionSuccess, OnFinished}
import pl.touk.nussknacker.ui.listener.{ProcessChangeListener, User => ListenerUser}
import pl.touk.nussknacker.ui.process.processingtypedata.ProcessingTypeDataProvider
import pl.touk.nussknacker.ui.process.repository.ProcessDBQueryRepository.ProcessNotFoundError
import pl.touk.nussknacker.ui.process.repository.{DbProcessActionRepository, FetchingProcessRepository}
import pl.touk.nussknacker.ui.security.api.{LoggedUser, NussknackerInternalUser}
import pl.touk.nussknacker.ui.util.FailurePropagatingActor

import java.time.LocalDateTime
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal
import scala.util.{Failure, Success}

object ManagementActor {
  def props(managers: ProcessingTypeDataProvider[DeploymentManager],
            processRepository: FetchingProcessRepository[Future],
            processActionRepository: DbProcessActionRepository,
            scenarioResolver: ScenarioResolver,
            processChangeListener: ProcessChangeListener,
            deploymentService: DeploymentService)
           (implicit context: ActorRefFactory): Props = {
    Props(classOf[ManagementActor], managers, processRepository, processActionRepository, scenarioResolver, processChangeListener, deploymentService)
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
                      deployedProcessRepository: DbProcessActionRepository,
                      scenarioResolver: ScenarioResolver,
                      processChangeListener: ProcessChangeListener,
                      deploymentService: DeploymentService) extends FailurePropagatingActor with LazyLogging {

  private var beingDeployed = Map[ProcessName, DeployInfo]()

  private implicit val ec: ExecutionContext = context.dispatcher

  override def receive: PartialFunction[Any, Unit] = {
    case Deploy(process, user, savepointPath, comment) =>
      ensureNoDeploymentRunning {
        val deployRes = deploymentService.deployProcess(process.id, savepointPath, comment, performDeploy)(user)
        reply(withDeploymentInfo(process, user, DeploymentActionType.Deployment, comment, deployRes))
      }
    case Snapshot(id, user, savepointDir) =>
      reply(deploymentManager(id.id)(ec, user).flatMap(_.savepoint(id.name, savepointDir)))
    case Stop(id, user, savepointDir) =>
      reply(deploymentManager(id.id)(ec, user).flatMap(_.stop(id.name, savepointDir, toManagerUser(user))))
    case Cancel(id, user, comment) =>
      ensureNoDeploymentRunning {
        implicit val loggedUser: LoggedUser = user
        val cancelRes = deploymentService.cancelProcess(id, comment, performCancel)
        reply(withDeploymentInfo(id, user, DeploymentActionType.Cancel, comment, cancelRes))
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

    case DeploymentActionFinished(process, user, result) =>
      implicit val listenerUser: ListenerUser = ListenerApiUser(user)
      result match {
        case Left(failure) =>
          logger.error(s"Action: ${beingDeployed.get(process.name)} of $process finished with failure", failure)
          processChangeListener.handle(OnDeployActionFailed(process.id, failure))
        case Right(details) =>
          logger.info(s"Finishing ${beingDeployed.get(process.name)} of $process")
          processChangeListener.handle(OnDeployActionSuccess(process.id, details.version, details.comment, details.deployedAt, details.action))
      }
      beingDeployed -= process.name
    case Test(id, canonicalProcess, testData, user, encoder) =>
      ensureNoDeploymentRunning {
        implicit val loggedUser: LoggedUser = user
        val testAction = for {
          manager <- deploymentManager(id.id)
          resolvedProcess <- Future.fromTry(scenarioResolver.resolveScenario(canonicalProcess))
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
      _ <- handleFinishedProcess(processIdWithName, state)
    } yield handleObsoleteStatus(state, actions.headOption)

  private def findJobState(deploymentManager: DeploymentManager, processIdWithName: ProcessIdWithName)(implicit user: LoggedUser): Future[Option[ProcessState]] =
    deploymentManager.findJobStatus(processIdWithName.name).recover {
      case NonFatal(e) =>
        logger.warn(s"Failed to get status of ${processIdWithName}: ${e.getMessage}", e)
        Some(SimpleProcessStateDefinitionManager.processState(SimpleStateStatus.FailedToGet))
    }

  //This method handles some corner cases like retention for keeping old states - some engine can cleanup canceled states. It's more Flink hermetic.
  //TODO: In future we should move this functionality to DeploymentManager.
  private def handleObsoleteStatus(processState: Option[ProcessState], lastAction: Option[ProcessAction]): ProcessState =
    (processState, lastAction) match {
      case (Some(state), _) if state.status.isFailed => state
      case (_, Some(action)) if action.isDeployed => handleMismatchDeployedLastAction(processState, action)
      case (Some(state), _) if state.isDeployed => handleFollowingDeployState(state, lastAction)
      case (_, Some(action)) if action.isCanceled => handleCanceledState(processState)
      case (Some(state), _) => handleState(state, lastAction)
      case (None, Some(_)) => SimpleProcessStateDefinitionManager.processState(SimpleStateStatus.NotDeployed)
      case (None, None) => SimpleProcessStateDefinitionManager.processState(SimpleStateStatus.NotDeployed)
    }

  //TODO: In future we should move this functionality to DeploymentManager.
  private def handleState(state: ProcessState, lastAction: Option[ProcessAction]): ProcessState =
    state.status match {
      case SimpleStateStatus.NotDeployed if lastAction.isEmpty =>
        SimpleProcessStateDefinitionManager.processState(SimpleStateStatus.NotDeployed)
      //TODO: Should FlinkStateStatus.Restarting also be here?. Currently it's not handled to
      //avoid dependency on FlinkDeploymentManager
      case SimpleStateStatus.DuringCancel | SimpleStateStatus.Finished if lastAction.isEmpty =>
        state.withStatusDetails(SimpleProcessStateDefinitionManager.warningProcessWithoutActionState)
      case _ => state
    }

  //Thise method handles some corner cases for canceled process -> with last action = Canceled
  //TODO: In future we should move this functionality to DeploymentManager.
  private def handleCanceledState(processState: Option[ProcessState]): ProcessState =
    processState match {
      case Some(state) => state.status match {
        case _ => state
      }
      case None => SimpleProcessStateDefinitionManager.processState(SimpleStateStatus.Canceled)
    }

  //This method handles some corner cases for following deploy state mismatch last action version
  //TODO: In future we should move this functionality to DeploymentManager.
  private def handleFollowingDeployState(state: ProcessState, lastAction: Option[ProcessAction]): ProcessState =
    lastAction match {
      case Some(action) if !action.isDeployed =>
        state.withStatusDetails(SimpleProcessStateDefinitionManager.warningShouldNotBeRunningState(true))
      case Some(_) =>
        state
      case None =>
        state.withStatusDetails(SimpleProcessStateDefinitionManager.warningShouldNotBeRunningState(false))
    }

  //This method handles some corner cases for deployed action mismatch state version
  //TODO: In future we should move this functionality to DeploymentManager.
  private def handleMismatchDeployedLastAction(processState: Option[ProcessState], action: ProcessAction): ProcessState =
    processState match {
      case Some(state) =>
        state.version match {
          case _ if !state.isDeployed =>
            state.withStatusDetails(SimpleProcessStateDefinitionManager.errorShouldBeRunningState(action.processVersionId, action.user))
          case Some(ver) if ver.versionId != action.processVersionId =>
            state.withStatusDetails(SimpleProcessStateDefinitionManager.errorMismatchDeployedVersionState(ver.versionId, action.processVersionId, action.user))
          case Some(ver) if ver.versionId == action.processVersionId =>
            state
          case None => //TODO: we should remove Option from ProcessVersion?
            state.withStatusDetails(SimpleProcessStateDefinitionManager.warningMissingDeployedVersionState(action.processVersionId, action.user))
          case _ =>
            SimpleProcessStateDefinitionManager.processState(SimpleStateStatus.Error) //Generic error in other cases
        }
      case None =>
        SimpleProcessStateDefinitionManager.errorShouldBeRunningState(action.processVersionId, action.user)
    }

  //TODO: there is small problem here: if no one invokes process status for long time, Flink can remove process from history
  //- then it's gone, not finished.
  private def handleFinishedProcess(idWithName: ProcessIdWithName, processState: Option[ProcessState]): Future[Unit] = {
    implicit val user: NussknackerInternalUser.type = NussknackerInternalUser
    implicit val listenerUser: ListenerUser = ListenerApiUser(user)
    processState match {
      case Some(state) if state.status.isFinished =>
        findDeployedVersion(idWithName).flatMap {
          case Some(version) =>
            deployedProcessRepository.markProcessAsCancelled(idWithName.id, version, Some("Scenario finished")).map(_ =>
              processChangeListener.handle(OnFinished(idWithName.id, version))
            )
          case _ => Future.successful(())
        }
      case _ => Future.successful(())
    }
  }

  private def withDeploymentInfo(id: ProcessIdWithName, user: LoggedUser, action: DeploymentActionType, comment: Option[String],
                                 actionFuture: => Future[ProcessActionEntityData]): Future[ProcessActionEntityData] = {
    beingDeployed += id.name -> DeployInfo(user.username, System.currentTimeMillis(), action)
    actionFuture.onComplete {
      case Success(details) => self ! DeploymentActionFinished(id, user, Right(DeploymentDetails(details.processVersionId, comment,details.performedAtTime, details.action)))
      case Failure(ex) => self ! DeploymentActionFinished(id, user, Left(ex))
    }
    actionFuture
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

  private def findDeployedVersion(processId: ProcessIdWithName)(implicit user: LoggedUser): Future[Option[VersionId]] = for {
    process <- processRepository.fetchLatestProcessDetailsForProcessId[Unit](processId.id)
    lastAction = process.flatMap(_.lastDeployedAction)
  } yield lastAction.map(la => la.processVersionId)


  private def performDeploy(processingType: ProcessingType, processVersion: ProcessVersion, deploymentData: DeploymentData, canonicalProcess: CanonicalProcess, savepointPath: Option[String]): Future[Option[ExternalDeploymentId]] = {
    managers.forTypeUnsafe(processingType).deploy(processVersion, deploymentData, canonicalProcess, savepointPath)
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

case class Deploy(id: ProcessIdWithName, user: LoggedUser, savepointPath: Option[String], comment: Option[String]) extends DeploymentAction

case class Cancel(id: ProcessIdWithName, user: LoggedUser, comment: Option[String]) extends DeploymentAction

case class Snapshot(id: ProcessIdWithName, user: LoggedUser, savepointDir: Option[String])

case class Stop(id: ProcessIdWithName, user: LoggedUser, savepointDir: Option[String])

case class CheckStatus(id: ProcessIdWithName, user: LoggedUser)

case class Test[T](id: ProcessIdWithName, canonicalProcess: CanonicalProcess, test: TestData, user: LoggedUser, variableEncoder: Any => T)

case class DeploymentDetails(version: VersionId, comment: Option[String], deployedAt: LocalDateTime, action: ProcessActionType)

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
