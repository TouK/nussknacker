package pl.touk.nussknacker.ui.process.deployment

import java.time.LocalDateTime

import akka.actor.{ActorRefFactory, Props, Status}
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.ProcessingTypeData.ProcessingType
import pl.touk.nussknacker.engine.api.deployment.TestProcess.TestData
import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.customs.deployment.CustomStateStatus
import pl.touk.nussknacker.engine.marshall.ProcessMarshaller
import pl.touk.nussknacker.ui.listener.ProcessChangeEvent.{OnDeployActionFailed, OnDeployActionSuccess}
import pl.touk.nussknacker.restmodel.displayedgraph.{DisplayableProcess, ProcessStatus}
import pl.touk.nussknacker.restmodel.process.{ProcessId, ProcessIdWithName}
import pl.touk.nussknacker.restmodel.processdetails.DeploymentAction
import pl.touk.nussknacker.ui.EspError
import pl.touk.nussknacker.ui.db.entity.{DeployedProcessInfoEntityData, ProcessVersionEntityData}
import pl.touk.nussknacker.ui.listener.ProcessChangeListener
import pl.touk.nussknacker.ui.process.repository.ProcessRepository.ProcessNotFoundError
import pl.touk.nussknacker.ui.process.repository.{DeployedProcessRepository, FetchingProcessRepository}
import pl.touk.nussknacker.ui.process.subprocess.SubprocessResolver
import pl.touk.nussknacker.ui.security.api.{LoggedUser, NussknackerInternalUser}
import pl.touk.nussknacker.ui.util.{CatsSyntax, FailurePropagatingActor}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

object ManagementActor {
  def props(environment: String,
            managers: Map[ProcessingType, ProcessManager],
            processRepository: FetchingProcessRepository[Future],
            deployedProcessRepository: DeployedProcessRepository,
            subprocessResolver: SubprocessResolver,
            processChangeListener: ProcessChangeListener)
           (implicit context: ActorRefFactory): Props = {
    Props(classOf[ManagementActor], environment, managers, processRepository, deployedProcessRepository, subprocessResolver, processChangeListener)
  }
}

class ManagementActor(environment: String,
                      managers: Map[ProcessingType, ProcessManager],
                      processRepository: FetchingProcessRepository[Future],
                      deployedProcessRepository: DeployedProcessRepository,
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
      } yield sender() ! Some(ProcessStatus(
        deploymentId = None,
        status = CustomStateStatus.DuringDeploy,
        allowedActions = manager.processStateConfigurator.getStatusActions(CustomStateStatus.DuringDeploy),
        startTime = Some(info.time)
      ))
      reply(processStatus)

    case CheckStatus(id, user) =>
      implicit val loggedUser: LoggedUser = user

      val processStatus = for {
        deployedVersions <- processRepository.fetchDeploymentHistory(id.id)
        deployedVersion = deployedVersions.headOption.filter(_.deploymentAction == DeploymentAction.Deploy)
        manager <- processManager(id.id)
        state <- manager.findJobStatus(id.name)
        _ <- handleFinishedProcess(id, state)
      } yield state.map(ProcessStatus(_, deployedVersion.map(_.processVersionId)))
      reply(processStatus)

    case DeploymentActionFinished(process, user, result) =>
      implicit val loggedUser = user
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
  }

  //TODO: there is small problem here: if no one invokes process status for long time, Flink can remove process from history
  //- then it's gone, not finished.
  private def handleFinishedProcess(idWithName: ProcessIdWithName, processState: Option[ProcessState]): Future[Unit] = {
    implicit val user: NussknackerInternalUser.type = NussknackerInternalUser
    processState match {
      case Some(state) if StatusState.isFinished(state) =>
        findDeployedVersion(idWithName).flatMap {
          case Some(version) =>
            deployedProcessRepository.markProcessAsCancelled(idWithName.id, version, environment, Some("Process finished")).map(_ => ())
          case _ => Future.successful(())
        }
      case _ => Future.successful(())
    }
  }

  private def withDeploymentInfo(id: ProcessIdWithName, user: LoggedUser, action: DeploymentActionType, comment: Option[String],
                                 actionFuture: => Future[DeployedProcessInfoEntityData]): Future[DeployedProcessInfoEntityData] = {
    beingDeployed += id.name -> DeployInfo(user.username, System.currentTimeMillis(), action)
    actionFuture.onComplete {
      case Success(details) => self ! DeploymentActionFinished(id, user, Right(DeploymentDetails(details.processVersionId, comment,details.deployedAtTime, details.deploymentAction)))
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

  private def cancelProcess(processId: ProcessIdWithName, comment: Option[String])(implicit user: LoggedUser): Future[DeployedProcessInfoEntityData] = {
    for {
      manager <- processManager(processId.id)
      _ <- manager.cancel(processId.name)
      maybeVersion <- findDeployedVersion(processId)
      version <- maybeVersion match {
        case Some(processVersionId) => Future.successful(processVersionId)
        case None => Future.failed(ProcessNotFoundError(processId.name.value.toString))
      }
      result <- deployedProcessRepository.markProcessAsCancelled(processId.id, version, environment, comment)
    } yield result
  }

  private def findDeployedVersion(processId: ProcessIdWithName)(implicit user: LoggedUser) : Future[Option[Long]] = for {
    process <- processRepository.fetchLatestProcessDetailsForProcessId[Unit](processId.id)
    currentDeploymentInfo = process.flatMap(_.deployment)
  } yield (currentDeploymentInfo.map(_.processVersionId))


  private def deployProcess(processId: ProcessId, savepointPath: Option[String], comment: Option[String])
                           (implicit user: LoggedUser): Future[DeployedProcessInfoEntityData] = {
    for {
      processingType <- processRepository.fetchProcessingType(processId)
      latestProcessEntity <- processRepository.fetchLatestProcessVersion[DisplayableProcess](processId)
      result <- latestProcessEntity match {
        case Some(latestVersion) => deployAndSaveProcess(processingType, latestVersion, savepointPath, comment)
        case None => Future.failed(ProcessNotFoundError(processId.value.toString))
      }
    } yield result
  }

  private def deployAndSaveProcess(processingType: ProcessingType, latestVersion: ProcessVersionEntityData,
                                   savepointPath: Option[String], comment: Option[String])(implicit user: LoggedUser): Future[DeployedProcessInfoEntityData] = {
    val resolvedDeploymentData = resolveDeploymentData(latestVersion.deploymentData)
    val processManagerValue = managers(processingType)

    for {
      deploymentResolved <- resolvedDeploymentData
      maybeProcessName <- processRepository.fetchProcessName(ProcessId(latestVersion.processId))
      processName = maybeProcessName.getOrElse(throw new IllegalArgumentException(s"Unknown process Id ${latestVersion.processId}"))
      _ <- processManagerValue.deploy(latestVersion.toProcessVersion(processName), deploymentResolved, savepointPath)
      deployedActionData <- deployedProcessRepository.markProcessAsDeployed(ProcessId(latestVersion.processId), latestVersion.id,
        processingType, environment, comment)
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

  private def processManager(processId: ProcessId)(implicit ec: ExecutionContext, user: LoggedUser) = {
    processRepository.fetchProcessingType(processId).map(managers)
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
}

trait DeploymentAction {
  def id: ProcessIdWithName
}

case class Deploy(id: ProcessIdWithName, user: LoggedUser, savepointPath: Option[String], comment: Option[String]) extends DeploymentAction

case class Cancel(id: ProcessIdWithName, user: LoggedUser, comment: Option[String]) extends DeploymentAction

case class Snapshot(id: ProcessIdWithName, user: LoggedUser, savepointPath: String)

case class CheckStatus(id: ProcessIdWithName, user: LoggedUser)

case class Test[T](id: ProcessIdWithName, processJson: String, test: TestData, user: LoggedUser, variableEncoder: Any => T)

import pl.touk.nussknacker.restmodel.processdetails.{ DeploymentAction => Action }
case class DeploymentDetails(version: Long, comment: Option[String], deployedAt: LocalDateTime, action: Action.Value)
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
