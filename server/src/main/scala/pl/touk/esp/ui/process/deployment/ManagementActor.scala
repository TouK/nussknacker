package pl.touk.esp.ui.process.deployment

import akka.actor.{Actor, ActorRef, ActorRefFactory, Props, Status}
import com.typesafe.scalalogging.LazyLogging
import pl.touk.esp.engine.api.deployment.test.TestData
import pl.touk.esp.engine.api.deployment.{CustomProcess, GraphProcess, ProcessManager}
import pl.touk.esp.ui.EspError
import pl.touk.esp.ui.db.entity.ProcessVersionEntity.ProcessVersionEntityData
import pl.touk.esp.ui.process.displayedgraph.ProcessStatus
import pl.touk.esp.ui.process.repository.ProcessRepository.ProcessNotFoundError
import pl.touk.esp.ui.process.repository.{DeployedProcessRepository, ProcessRepository}
import pl.touk.esp.ui.security.LoggedUser

import scala.concurrent.Future
import scala.util.control.NonFatal
import scala.util.{Failure, Success}

object ManagementActor {
  def apply(environment: String, processManager: ProcessManager,
                        processRepository: ProcessRepository,
                        deployedProcessRepository: DeployedProcessRepository)(implicit context: ActorRefFactory) : ActorRef = {
    context.actorOf(Props(classOf[ManagementActor], environment, processManager, processRepository, deployedProcessRepository))
  }

}

class ManagementActor(environment: String, processManager: ProcessManager,
                      processRepository: ProcessRepository,
                      deployedProcessRepository: DeployedProcessRepository) extends Actor with LazyLogging {

  var beingDeployed = Map[String, DeployInfo]()

  implicit val ec = context.dispatcher

  override def receive = {
    case a: DeploymentAction if isBeingDeployed(a.id) =>
      sender() ! Status.Failure(new ProcessIsBeingDeployed(a.id, beingDeployed(a.id)))
    case Deploy(id, user) =>
      val deployRes: Future[_] = deployProcess(id)(user)
      reply(withDeploymentInfo(id, user.id, "Deployment", deployRes))
    case Cancel(id, user) =>
      val cancelRes = processManager.cancel(id).flatMap(_ => deployedProcessRepository.markProcessAsCancelled(id, user.id, environment))
      reply(withDeploymentInfo(id, user.id, "Cancel", cancelRes))
    case CheckStatus(id) if isBeingDeployed(id) =>
      val info = beingDeployed(id)
      sender() ! Some(ProcessStatus(None, s"${info.action} IN PROGRESS", info.time, false, true))
    case CheckStatus(id) =>
      reply(processManager.findJobStatus(id).map(_.map(ProcessStatus.apply)))
    case DeploymentActionFinished(id) =>
      logger.info(s"Finishing ${beingDeployed.get(id)} of $id")
      beingDeployed -= id
    case Test(processId, testData, user) =>
      implicit val loggedUser = user
      reply(processRepository.fetchLatestProcessVersion(processId).flatMap {
        case Some(version) => processManager.test(processId, version.deploymentData, testData)
        case None => Future(ProcessNotFoundError(processId))
      })

  }

  private def withDeploymentInfo(id: String, userId: String, actionName: String, action: => Future[_]) = {
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

  private def deployProcess(processId: String)(implicit user: LoggedUser) = {
    processRepository.fetchLatestProcessVersion(processId).flatMap {
      case Some(latestVersion) => deployAndSaveProcess(latestVersion)
      case None => Future(ProcessNotFoundError(processId))
    }
  }

  private def deployAndSaveProcess(latestVersion: ProcessVersionEntityData)(implicit user: LoggedUser): Future[Unit] = {
    val processId = latestVersion.processId
    logger.debug(s"Deploy of $processId started")
    val deployment = latestVersion.deploymentData
    processManager.deploy(processId, deployment).flatMap { _ =>
      logger.debug(s"Deploy of $processId finished")
      deployedProcessRepository.markProcessAsDeployed(latestVersion, user.id, environment).recoverWith { case NonFatal(e) =>
        logger.error("Error during marking process as deployed", e)
        processManager.cancel(processId).map(_ => Future.failed(e))
      }
    }
  }

}


trait DeploymentAction {
  def id: String
}

case class Deploy(id: String, user:LoggedUser) extends DeploymentAction

case class Cancel(id: String, user:LoggedUser) extends DeploymentAction

case class CheckStatus(id: String)

case class Test(id: String, test: TestData, user:LoggedUser)

case class DeploymentActionFinished(id: String)

case class DeployInfo(userId: String, time: Long, action: String)

class ProcessIsBeingDeployed(id: String, info: DeployInfo) extends
  Exception(s"${info.action} is currently performed on $id by ${info.userId}") with EspError
