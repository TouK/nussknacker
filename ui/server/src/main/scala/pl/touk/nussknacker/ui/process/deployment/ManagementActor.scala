package pl.touk.nussknacker.ui.process.deployment

import akka.actor.{Actor, ActorRef, ActorRefFactory, Props, Status}
import argonaut.{Json, PrettyParams}
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api.deployment.TestProcess.{TestData, TestResults}
import pl.touk.nussknacker.engine.api.deployment.{GraphProcess, ProcessDeploymentData, ProcessManager}
import pl.touk.nussknacker.ui.EspError
import pl.touk.nussknacker.ui.codec.UiCodecs
import pl.touk.nussknacker.ui.db.entity.ProcessEntity.ProcessingType.ProcessingType
import pl.touk.nussknacker.ui.db.entity.ProcessVersionEntity.ProcessVersionEntityData
import pl.touk.nussknacker.ui.process.displayedgraph.ProcessStatus
import pl.touk.nussknacker.ui.process.marshall.UiProcessMarshaller
import pl.touk.nussknacker.ui.process.repository.ProcessRepository.ProcessNotFoundError
import pl.touk.nussknacker.ui.process.repository.{DeployedProcessRepository, FetchingProcessRepository}
import pl.touk.nussknacker.ui.process.subprocess.SubprocessResolver
import pl.touk.nussknacker.ui.security.api.LoggedUser
import pl.touk.nussknacker.ui.util.CatsSyntax

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal
import scala.util.{Failure, Success}

object ManagementActor {
  def apply(environment: String,
            managers: Map[ProcessingType, ProcessManager],
            processRepository: FetchingProcessRepository,
            deployedProcessRepository: DeployedProcessRepository, subprocessResolver: SubprocessResolver)(implicit context: ActorRefFactory): ActorRef = {
    context.actorOf(Props(classOf[ManagementActor], environment, managers, processRepository, deployedProcessRepository, subprocessResolver))
  }

}

class ManagementActor(environment: String, managers: Map[ProcessingType, ProcessManager],
                      processRepository: FetchingProcessRepository,
                      deployedProcessRepository: DeployedProcessRepository, subprocessResolver: SubprocessResolver) extends Actor with LazyLogging {

  private var beingDeployed = Map[String, DeployInfo]()

  private implicit val ec = context.dispatcher

  override def receive = {
    case Deploy(id, user, savepointPath) =>
      ensureNoDeploymentRunning {
        val deployRes: Future[Unit] = deployProcess(id, savepointPath)(user)
        reply(withDeploymentInfo(id, user.id, "Deployment", deployRes))
      }
    case Snapshot(id, user, savepointDir) =>
      reply(processManager(id)(ec, user).flatMap(_.savepoint(id, savepointDir)))
    case Cancel(id, user) =>
      ensureNoDeploymentRunning {
        implicit val loggedUser = user
        val cancelRes = processManager(id).map { manager =>
          manager.cancel(id).flatMap(_ => deployedProcessRepository.markProcessAsCancelled(id, user.id, environment))
        }
        reply(withDeploymentInfo(id, user.id, "Cancel", cancelRes))
      }
    case CheckStatus(id, user) if isBeingDeployed(id) =>
      val info = beingDeployed(id)
      sender() ! Some(ProcessStatus(None, s"${info.action} IN PROGRESS", info.time, false, true))
    case CheckStatus(id, user) =>
      implicit val loggedUser = user
      val processStatus = processManager(id).flatMap { manager =>
        manager.findJobStatus(id).map(_.map(ProcessStatus.apply))
      }
      reply(processStatus)
    case DeploymentActionFinished(id) =>
      logger.info(s"Finishing ${beingDeployed.get(id)} of $id")
      beingDeployed -= id
    case Test(processId, processJson, testData, user, encoder) =>
      ensureNoDeploymentRunning {
        implicit val loggedUser = user
        val testAction = for {
          manager <- processManager(processId)
          resolvedProcess <- resolveGraph(processJson)
          testResult <- manager.test(processId, resolvedProcess, testData, encoder)
        } yield testResult
        reply(testAction)
      }
    case DeploymentStatus =>
      reply(Future.successful(DeploymentStatusResponse(beingDeployed)))
  }

  private def withDeploymentInfo[T](id: String, userId: String, actionName: String, action: => Future[T]): Future[T] = {
    beingDeployed += id -> DeployInfo(userId, System.currentTimeMillis(), actionName)
    action.onComplete(_ => self ! DeploymentActionFinished(id))
    action
  }

  private def reply(action: => Future[_]): Unit = {
    val replyTo = sender()
    action.onComplete {
      case Success(a) => replyTo ! a
      case Failure(ex) => replyTo ! Status.Failure(ex)
    }
  }

  private def isBeingDeployed(id: String) = beingDeployed.contains(id)

  private def deployProcess(processId: String, savepointPath: Option[String])(implicit user: LoggedUser): Future[Unit] = {
    for {
      processingType <- getProcessingType(processId)
      latestProcessEntity <- processRepository.fetchLatestProcessVersion(processId)
      result <- latestProcessEntity match {
        case Some(latestVersion) => deployAndSaveProcess(processingType, latestVersion, savepointPath)
        case None => Future.failed(ProcessNotFoundError(processId))
      }
    } yield result
  }

  private def deployAndSaveProcess(processingType: ProcessingType, latestVersion: ProcessVersionEntityData, savepointPath: Option[String])(implicit user: LoggedUser): Future[Unit] = {
    val resolvedDeploymentData = resolveDeploymentData(latestVersion.deploymentData)
    val processManagerValue = managers(processingType)

    for {
      deploymentResolved <- resolvedDeploymentData
      _ <- processManagerValue.deploy(latestVersion.toProcessVersion, deploymentResolved, savepointPath)
      _ <- deployedProcessRepository.markProcessAsDeployed(latestVersion.processId, latestVersion.id, processingType, user.id, environment)
    } yield ()
  }

  private def resolveDeploymentData(data: ProcessDeploymentData) = data match {
    case GraphProcess(canonical) =>
      resolveGraph(canonical).map(GraphProcess)
    case a =>
      Future.successful(a)
  }

  private def resolveGraph(canonicalJson: String): Future[String] = {
    val validatedGraph = UiProcessMarshaller.fromJson(canonicalJson).toValidatedNel
      .andThen(subprocessResolver.resolveSubprocesses)
      .map(UiProcessMarshaller.toJson(_, PrettyParams.spaces2))
    CatsSyntax.toFuture(validatedGraph)(e => new RuntimeException(e.head.toString))
  }

  private def processManager(processId: String)(implicit ec: ExecutionContext, user: LoggedUser) = {
    getProcessingType(processId).map(managers)
  }

  private def getProcessingType(id: String)(implicit ec: ExecutionContext, user: LoggedUser) = {
    processRepository.fetchLatestProcessDetailsForProcessId(id).map(_.map(_.processingType)).map(_.getOrElse(throw new RuntimeException(ProcessNotFoundError(id).getMessage)))
  }

  //during deployment using Client.run Flink holds some data in statics and there is an exception when
  //test or verification run in parallel
  private def ensureNoDeploymentRunning(action: => Unit) = {
    if (beingDeployed.nonEmpty) {
      sender() ! Status.Failure(new ProcessIsBeingDeployed(beingDeployed))
    } else {
      action
    }
  }

}


trait DeploymentAction {
  def id: String
}

case class Deploy(id: String, user: LoggedUser, savepointPath: Option[String]) extends DeploymentAction

case class Cancel(id: String, user: LoggedUser) extends DeploymentAction

case class Snapshot(id: String, user: LoggedUser, savepointPath: String)

case class CheckStatus(id: String, user: LoggedUser)

case class Test[T](processId: String, processJson: String, test: TestData, user: LoggedUser, variableEncoder: Any => T)

case class DeploymentActionFinished(id: String)

case class DeployInfo(userId: String, time: Long, action: String)

case object DeploymentStatus

case class DeploymentStatusResponse(deploymentInfo: Map[String, DeployInfo])


class ProcessIsBeingDeployed(deployments: Map[String, DeployInfo]) extends
  Exception(s"Cannot deploy/test as following deployments are in progress: ${
    deployments.map {
      case (id, info) => s"${info.action} on $id by ${info.userId}"
    }.mkString(", ")
  }") with EspError

