package pl.touk.nussknacker.ui.process.deployment

import akka.actor.{Actor, ActorRef, ActorRefFactory, Props, Status}
import argonaut.PrettyParams
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api.deployment.test.TestData
import pl.touk.nussknacker.engine.api.deployment.{GraphProcess, ProcessManager}
import pl.touk.nussknacker.ui.EspError
import pl.touk.nussknacker.ui.db.entity.ProcessEntity.ProcessingType.ProcessingType
import pl.touk.nussknacker.ui.db.entity.ProcessVersionEntity.ProcessVersionEntityData
import pl.touk.nussknacker.ui.process.displayedgraph.ProcessStatus
import pl.touk.nussknacker.ui.process.marshall.UiProcessMarshaller
import pl.touk.nussknacker.ui.process.repository.ProcessRepository.ProcessNotFoundError
import pl.touk.nussknacker.ui.process.repository.{DeployedProcessRepository, FetchingProcessRepository}
import pl.touk.nussknacker.ui.process.subprocess.SubprocessResolver
import pl.touk.nussknacker.ui.security.api.LoggedUser

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
    case Test(processId, processJson, testData, user) =>
      ensureNoDeploymentRunning {
        implicit val loggedUser = user
        val testAction = processManager(processId).flatMap { manager =>
          manager.test(processId, resolveGraph(processJson), testData)
        }
        reply(testAction)
      }
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
    val processId = latestVersion.processId
    logger.debug(s"Deploy of $processId started")
    val deployment = latestVersion.deploymentData match {
      case GraphProcess(canonical) => GraphProcess(resolveGraph(canonical))
      case a => a
    }
    val processManagerValue = managers(processingType)
    processManagerValue.deploy(processId, deployment, savepointPath).flatMap { _ =>
      logger.debug(s"Deploy of $processId finished")
      deployedProcessRepository.markProcessAsDeployed(processingType, latestVersion, user.id, environment).recoverWith { case NonFatal(e) =>
        logger.error("Error during marking process as deployed", e)
        processManagerValue.cancel(processId).map(_ => Future.failed(e))
      }
    }
  }

  private def resolveGraph(canonicalJson: String): String = {
    UiProcessMarshaller.toJson(UiProcessMarshaller.fromJson(canonicalJson).andThen(subprocessResolver.resolveSubprocesses).toOption.get, PrettyParams.spaces2)
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

case class Test(processId: String, processJson: String, test: TestData, user: LoggedUser)

case class DeploymentActionFinished(id: String)

case class DeployInfo(userId: String, time: Long, action: String)

class ProcessIsBeingDeployed(deployments: Map[String, DeployInfo]) extends
  Exception(s"Cannot deploy/test as following deployments are in progress: ${
    deployments.map {
      case (id, info) => s"${info.action} on $id by ${info.userId}"
    }.mkString(", ")
  }") with EspError

