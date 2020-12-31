package pl.touk.nussknacker.ui.process.deployment

import java.time.LocalDateTime
import akka.actor.{ActorRefFactory, Props, Status}
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.ProcessingTypeData.ProcessingType
import pl.touk.nussknacker.engine.api.deployment
import pl.touk.nussknacker.engine.api.deployment.ProcessActionType.ProcessActionType
import pl.touk.nussknacker.engine.api.deployment.TestProcess.TestData
import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.marshall.ProcessMarshaller
import pl.touk.nussknacker.ui.listener.ProcessChangeEvent.{OnDeployActionFailed, OnDeployActionSuccess, OnFinished}
import pl.touk.nussknacker.restmodel.displayedgraph.{DisplayableProcess, ProcessStatus}
import pl.touk.nussknacker.restmodel.process.{ProcessId, ProcessIdWithName}
import pl.touk.nussknacker.restmodel.processdetails.ProcessAction
import pl.touk.nussknacker.ui.EspError
import pl.touk.nussknacker.ui.db.entity.{ProcessActionEntityData, ProcessVersionEntityData}
import pl.touk.nussknacker.ui.listener.ProcessChangeListener
import pl.touk.nussknacker.ui.process.processingtypedata.ProcessingTypeDataProvider
import pl.touk.nussknacker.ui.process.repository.ProcessRepository.ProcessNotFoundError
import pl.touk.nussknacker.ui.process.repository.{FetchingProcessRepository, ProcessActionRepository}
import pl.touk.nussknacker.ui.process.subprocess.SubprocessResolver
import pl.touk.nussknacker.ui.security.api.{LoggedUser, NussknackerInternalUser}
import pl.touk.nussknacker.ui.util.{CatsSyntax, FailurePropagatingActor}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

object ManagementActor {
  def props(managers: ProcessingTypeDataProvider[ProcessManager],
            processRepository: FetchingProcessRepository[Future],
            deployedProcessRepository: ProcessActionRepository,
            subprocessResolver: SubprocessResolver,
            processChangeListener: ProcessChangeListener)
           (implicit context: ActorRefFactory): Props = {
    Props(classOf[ManagementActor], managers, processRepository, deployedProcessRepository, subprocessResolver, processChangeListener)
  }
}

class ManagementActor(managers: ProcessingTypeDataProvider[ProcessManager],
                      processRepository: FetchingProcessRepository[Future],
                      deployedProcessRepository: ProcessActionRepository,
                      subprocessResolver: SubprocessResolver,
                      processChangeListener: ProcessChangeListener) extends FailurePropagatingActor with LazyLogging {

  private var beingDeployed = Map[ProcessName, DeployInfo]()

  private implicit val ec: ExecutionContext = context.dispatcher

  override def receive: PartialFunction[Any, Unit] = {
    case Deploy(process, user, savepointPath, comment) =>
      ensureNoDeploymentRunning {
        val deployRes = deployProcess(process.id, savepointPath, comment)(user)
        reply(withDeploymentInfo(process, user, DeploymentActionType.Deployment, comment, deployRes))
      }
    case Snapshot(id, user, savepointDir) =>
      reply(processManager(id.id)(ec, user).flatMap(_.savepoint(id.name, savepointDir)))
    case Stop(id, user, savepointDir) =>
      reply(processManager(id.id)(ec, user).flatMap(_.stop(id.name, savepointDir, toManagerUser(user))))
    case Cancel(id, user, comment) =>
      ensureNoDeploymentRunning {
        implicit val loggedUser: LoggedUser = user
        val cancelRes = cancelProcess(id, comment)
        reply(withDeploymentInfo(id, user, DeploymentActionType.Cancel, comment, cancelRes))
      }
    case CheckStatus(id, user) if isBeingDeployed(id.name) =>
      implicit val loggedUser: LoggedUser = user
      val info = beingDeployed(id.name)

      val processStatus = for {
        manager <- processManager(id.id)
      } yield Some(ProcessStatus(
        SimpleStateStatus.DuringDeploy,
        manager.processStateDefinitionManager,
        deploymentId = Option.empty,
        startTime = Some(info.time),
        attributes = Option.empty,
        errors = List.empty
      ))
      reply(processStatus)

    case CheckStatus(id, user) =>
      implicit val loggedUser: LoggedUser = user

      val processStatus = for {
        actions <- processRepository.fetchProcessActions(id.id)
        manager <- processManager(id.id)
        state <- manager.findJobStatus(id.name)
        _ <- handleFinishedProcess(id, state)
      } yield Option(handleObsoleteStatus(state, actions.headOption))
      reply(processStatus)

    case DeploymentActionFinished(process, user, result) =>
      implicit val loggedUser: LoggedUser = user
      result match {
        case Left(failure) =>
          logger.error(s"Action: ${beingDeployed.get(process.name)} of $process finished with failure", failure)
          processChangeListener.handle(OnDeployActionFailed(process.id, failure))
        case Right(details) =>
          logger.info(s"Finishing ${beingDeployed.get(process.name)} of $process")
          processChangeListener.handle(OnDeployActionSuccess(process.id, details.version, details.comment, details.deployedAt, details.action))
      }
      beingDeployed -= process.name
    case Test(id, processJson, testData, user, encoder) =>
      ensureNoDeploymentRunning {
        implicit val loggedUser: LoggedUser = user
        val testAction = for {
          manager <- processManager(id.id)
          resolvedProcess <- resolveGraph(processJson)
          testResult <- manager.test(id.name, resolvedProcess, testData, encoder)
        } yield testResult
        reply(testAction)
      }
    case DeploymentStatus =>
      reply(Future.successful(DeploymentStatusResponse(beingDeployed)))

    case (action: CustomActionRequest, id: ProcessId, user: LoggedUser) =>
      reply(for {
        manager <- processManager(id)(ec, user)
        response <- manager.invokeCustomAction(action)
      } yield response)
  }

  //This method handles some corner cases like retention for keeping old states - some engine can cleanup canceled states. It's more Flink hermetic.
  //TODO: In future we should move this functionality to ProcessManager.
  private def handleObsoleteStatus(processState: Option[ProcessState], lastAction: Option[ProcessAction]): ProcessStatus =
    (processState, lastAction) match {
      case (Some(state), _) if state.status.isFailed => ProcessStatus(state)
      case (_, Some(action)) if action.isDeployed => handleMismatchDeployedLastAction(processState, action)
      case (Some(state), _) if state.status.isFollowingDeployAction => handleFollowingDeployState(state, lastAction)
      case (_, Some(action)) if action.isCanceled => handleCanceledState(processState)
      case (Some(state), _) => handleState(state, lastAction)
      case (None, None) => ProcessStatus.simple(SimpleStateStatus.NotDeployed)
    }

  //TODO: In future we should move this functionality to ProcessManager.
  private def handleState(state: ProcessState, lastAction: Option[ProcessAction]): ProcessStatus =
    state.status match {
      case SimpleStateStatus.NotFound | SimpleStateStatus.NotDeployed if lastAction.isEmpty =>
        ProcessStatus.simple(SimpleStateStatus.NotDeployed)
      //TODO: Should FlinkStateStatus.Restarting also be here?. Currently it's not handled to
      //avoid dependency on FlinkProcessManager
      case SimpleStateStatus.DuringCancel | SimpleStateStatus.Finished if lastAction.isEmpty =>
        ProcessStatus.simpleWarningProcessWithoutAction(Some(state))
      case _ => ProcessStatus(state)
    }

  //Thise method handles some corner cases for canceled process -> with last action = Canceled
  //TODO: In future we should move this functionality to ProcessManager.
  private def handleCanceledState(processState: Option[ProcessState]): ProcessStatus =
    processState match {
      case Some(state) => state.status match {
        case SimpleStateStatus.NotFound => ProcessStatus.simple(SimpleStateStatus.Canceled)
        case _ => ProcessStatus(state)
      }
      case None => ProcessStatus.simple(SimpleStateStatus.Canceled)
    }

  //This method handles some corner cases for following deploy state mismatch last action version
  //TODO: In future we should move this functionality to ProcessManager.
  private def handleFollowingDeployState(state: ProcessState, lastAction: Option[ProcessAction]): ProcessStatus =
    lastAction match {
      case Some(action) if action.isCanceled =>
        ProcessStatus.simpleWarningShouldNotBeRunning(Some(state), true)
      case Some(_) =>
        ProcessStatus(state)
      case None =>
        ProcessStatus.simpleWarningShouldNotBeRunning(Some(state), false)
    }

  //This method handles some corner cases for deployed action mismatch state version
  //TODO: In future we should move this functionality to ProcessManager.
  private def handleMismatchDeployedLastAction(processState: Option[ProcessState], action: ProcessAction): ProcessStatus =
    processState match {
      case Some(state) =>
        state.version match {
          case Some(_) if !state.status.isFollowingDeployAction =>
            ProcessStatus.simpleErrorShouldBeRunning(action.processVersionId, action.user, processState)
          case Some(ver) if ver.versionId != action.processVersionId =>
            ProcessStatus.simpleErrorMismatchDeployedVersion(ver.versionId, action.processVersionId, action.user, processState)
          case Some(ver) if ver.versionId == action.processVersionId =>
            ProcessStatus(state)
          case None => //TODO: we should remove Option from ProcessVersion?
            ProcessStatus.simpleWarningMissingDeployedVersion(action.processVersionId, action.user, processState)
          case _ =>
            ProcessStatus.simple(SimpleStateStatus.Error) //Generic
          // c error in other cases
        }
      case None =>
        ProcessStatus.simpleErrorShouldBeRunning(action.processVersionId, action.user, Option.empty)
    }

  //TODO: there is small problem here: if no one invokes process status for long time, Flink can remove process from history
  //- then it's gone, not finished.
  private def handleFinishedProcess(idWithName: ProcessIdWithName, processState: Option[ProcessState]): Future[Unit] = {
    implicit val user: NussknackerInternalUser.type = NussknackerInternalUser
    processState match {
      case Some(state) if state.status.isFinished =>
        findDeployedVersion(idWithName).flatMap {
          case Some(version) =>
            deployedProcessRepository.markProcessAsCancelled(idWithName.id, version, Some("Process finished")).map(_ =>
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

  private def cancelProcess(processId: ProcessIdWithName, comment: Option[String])(implicit user: LoggedUser): Future[ProcessActionEntityData] = {
    for {
      manager <- processManager(processId.id)
      _ <- manager.cancel(processId.name, toManagerUser(user))
      maybeVersion <- findDeployedVersion(processId)
      version <- maybeVersion match {
        case Some(processVersionId) => Future.successful(processVersionId)
        case None => Future.failed(ProcessNotFoundError(processId.name.value.toString))
      }
      result <- deployedProcessRepository.markProcessAsCancelled(processId.id, version, comment)
    } yield result
  }

  private def findDeployedVersion(processId: ProcessIdWithName)(implicit user: LoggedUser): Future[Option[Long]] = for {
    process <- processRepository.fetchLatestProcessDetailsForProcessId[Unit](processId.id)
    lastAction = process.flatMap(_.lastDeployedAction)
  } yield lastAction.map(_.processVersionId)

  private def deployProcess(processId: ProcessId, savepointPath: Option[String], comment: Option[String])
                           (implicit user: LoggedUser): Future[ProcessActionEntityData] = {
    for {
      processingType <- processRepository.fetchProcessingType(processId)
      latestProcessEntity <- processRepository.fetchLatestProcessVersion[DisplayableProcess](processId)
      result <- latestProcessEntity match {
        case Some(latestVersion) => deployAndSaveProcess(processingType, latestVersion, savepointPath, comment)
        case None => Future.failed(ProcessNotFoundError(processId.value.toString))
      }
    } yield result
  }

  private def deployAndSaveProcess(processingType: ProcessingType,
                                   latestVersion: ProcessVersionEntityData,
                                   savepointPath: Option[String],
                                   comment: Option[String])(implicit user: LoggedUser): Future[ProcessActionEntityData] = {
    val resolvedDeploymentData = resolveDeploymentData(latestVersion.deploymentData)
    val processManagerValue = managers.forTypeUnsafe(processingType)

    for {
      deploymentResolved <- resolvedDeploymentData
      maybeProcessName <- processRepository.fetchProcessName(ProcessId(latestVersion.processId))
      processName = maybeProcessName.getOrElse(throw new IllegalArgumentException(s"Unknown process Id ${latestVersion.processId}"))
      _ <- processManagerValue.deploy(latestVersion.toProcessVersion(processName), deploymentResolved, savepointPath, toManagerUser(user))
      deployedActionData <- deployedProcessRepository.markProcessAsDeployed(
        ProcessId(latestVersion.processId), latestVersion.id, processingType, comment
      )
    } yield deployedActionData
  }

  private def resolveDeploymentData(data: ProcessDeploymentData) = data match {
    case GraphProcess(canonical) =>
      resolveGraph(canonical).map(GraphProcess)
    case a =>
      Future.successful(a)
  }

  private def resolveGraph(canonicalJson: String): Future[String] = {
    val validatedGraph = ProcessMarshaller.fromJson(canonicalJson)
      .map(_.withoutDisabledNodes)
      .toValidatedNel
      .andThen(subprocessResolver.resolveSubprocesses)
      .map(proc => ProcessMarshaller.toJson(proc).noSpaces)
    CatsSyntax.toFuture(validatedGraph)(e => new RuntimeException(e.head.toString))
  }

  private def processManager(processId: ProcessId)(implicit ec: ExecutionContext, user: LoggedUser): Future[ProcessManager] = {
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

  private def toManagerUser(loggedUser: LoggedUser) = User(loggedUser.id, loggedUser.username)
}

trait DeploymentAction {
  def id: ProcessIdWithName
}

case class Deploy(id: ProcessIdWithName, user: LoggedUser, savepointPath: Option[String], comment: Option[String]) extends DeploymentAction

case class Cancel(id: ProcessIdWithName, user: LoggedUser, comment: Option[String]) extends DeploymentAction

case class Snapshot(id: ProcessIdWithName, user: LoggedUser, savepointDir: Option[String])

case class Stop(id: ProcessIdWithName, user: LoggedUser, savepointDir: Option[String])

case class CheckStatus(id: ProcessIdWithName, user: LoggedUser)

case class Test[T](id: ProcessIdWithName, processJson: String, test: TestData, user: LoggedUser, variableEncoder: Any => T)

case class DeploymentDetails(version: Long, comment: Option[String], deployedAt: LocalDateTime, action: ProcessActionType)
case class DeploymentActionFinished(id: ProcessIdWithName, user: LoggedUser, failureOrDetails: Either[Throwable, DeploymentDetails])

case class DeployInfo(userId: String, time: Long, action: DeploymentActionType)

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
