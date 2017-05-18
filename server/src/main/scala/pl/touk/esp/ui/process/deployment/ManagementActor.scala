package pl.touk.esp.ui.process.deployment

import akka.actor.{Actor, ActorRef, ActorRefFactory, Props, Status}
import argonaut.PrettyParams
import com.typesafe.scalalogging.LazyLogging
import pl.touk.esp.engine.api.deployment.test.TestData
import pl.touk.esp.engine.api.deployment.{CustomProcess, GraphProcess, ProcessManager}
import pl.touk.esp.engine.canonize.ProcessCanonizer
import pl.touk.esp.ui.EspError
import pl.touk.esp.ui.db.entity.ProcessEntity.ProcessingType.ProcessingType
import pl.touk.esp.ui.db.entity.ProcessVersionEntity.ProcessVersionEntityData
import pl.touk.esp.ui.process.displayedgraph.ProcessStatus
import pl.touk.esp.ui.process.marshall.UiProcessMarshaller
import pl.touk.esp.ui.process.repository.ProcessRepository.ProcessNotFoundError
import pl.touk.esp.ui.process.repository.{DeployedProcessRepository, ProcessRepository}
import pl.touk.esp.ui.process.subprocess.SubprocessResolver
import pl.touk.esp.ui.security.LoggedUser

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal
import scala.util.{Failure, Success}

object ManagementActor {
  def apply(environment: String,
            managers: Map[ProcessingType, ProcessManager],
            processRepository: ProcessRepository,
            deployedProcessRepository: DeployedProcessRepository, subprocessResolver: SubprocessResolver)(implicit context: ActorRefFactory): ActorRef = {
    context.actorOf(Props(classOf[ManagementActor], environment, managers, processRepository, deployedProcessRepository, subprocessResolver))
  }

}

class ManagementActor(environment: String, managers: Map[ProcessingType, ProcessManager],
                      processRepository: ProcessRepository,
                      deployedProcessRepository: DeployedProcessRepository, subprocessResolver: SubprocessResolver) extends Actor with LazyLogging {

  private var beingDeployed = Map[String, DeployInfo]()

  private implicit val ec = context.dispatcher

  private val marshaller = UiProcessMarshaller()

  override def receive = {
    case a: DeploymentAction if isBeingDeployed(a.id) =>
      sender() ! Status.Failure(new ProcessIsBeingDeployed(a.id, beingDeployed(a.id)))
    case Deploy(id, user) =>
      val deployRes: Future[_] = deployProcess(id)(user)
      reply(withDeploymentInfo(id, user.id, "Deployment", deployRes))
    case Cancel(id, user) =>
      implicit val loggedUser = user
      val cancelRes = processManager(id).map { manager =>
        manager.cancel(id).flatMap(_ => deployedProcessRepository.markProcessAsCancelled(id, user.id, environment))
      }
      reply(withDeploymentInfo(id, user.id, "Cancel", cancelRes))
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
      //to b. smutne, ale Flink przechowuje przy deploymencie za pomoca Client.run niektore rzeczy w staticu
      //i leci wyjatek jak sie testy rownlolegle pusci...
      implicit val loggedUser = user
      if (beingDeployed.nonEmpty) {
        sender() ! Status.Failure(ProcessIsBeingDeployedNoTestAllowed)
      } else {
        val testAction = processManager(processId).flatMap { manager =>
          manager.test(processId, resolveGraph(processJson), testData)
        }
        reply(testAction)
      }
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
    processManager(processId).flatMap { manager =>
      processRepository.fetchLatestProcessVersion(processId).flatMap {
        case Some(latestVersion) => deployAndSaveProcess(latestVersion, manager)
        case None => Future(ProcessNotFoundError(processId))
      }
    }
  }

  private def resolveGraph(canonicalJson: String) : String = {
    marshaller.toJson(marshaller.fromJson(canonicalJson).andThen(subprocessResolver.resolveSubprocesses).toOption.get, PrettyParams.spaces2)
  }

  private def deployAndSaveProcess(latestVersion: ProcessVersionEntityData, processManager: ProcessManager)(implicit user: LoggedUser): Future[Unit] = {
    val processId = latestVersion.processId
    logger.debug(s"Deploy of $processId started")
    val deployment = latestVersion.deploymentData match {
      case GraphProcess(canonical) => GraphProcess(resolveGraph(canonical))
      case a => a
    }
    processManager.deploy(processId, deployment).flatMap { _ =>
      logger.debug(s"Deploy of $processId finished")
      deployedProcessRepository.markProcessAsDeployed(latestVersion, user.id, environment).recoverWith { case NonFatal(e) =>
        logger.error("Error during marking process as deployed", e)
        processManager.cancel(processId).map(_ => Future.failed(e))
      }
    }
  }

  private def processManager(processId: String)(implicit ec: ExecutionContext, user: LoggedUser) = {
    processingType(processId).map(managers)
  }

  private def processingType(id: String)(implicit ec: ExecutionContext, user: LoggedUser) = {
    processRepository.fetchLatestProcessDetailsForProcessId(id).map(_.map(_.processingType)).map(_.getOrElse(throw new RuntimeException(ProcessNotFoundError(id).getMessage)))
  }
}


trait DeploymentAction {
  def id: String
}

case class Deploy(id: String, user:LoggedUser) extends DeploymentAction

case class Cancel(id: String, user:LoggedUser) extends DeploymentAction

case class CheckStatus(id: String, user:LoggedUser)

case class Test(processId: String, processJson: String, test: TestData, user:LoggedUser)

case class DeploymentActionFinished(id: String)

case class DeployInfo(userId: String, time: Long, action: String)

class ProcessIsBeingDeployed(id: String, info: DeployInfo) extends
  Exception(s"${info.action} is currently performed on $id by ${info.userId}") with EspError

object ProcessIsBeingDeployedNoTestAllowed extends
  Exception("Cannot run tests when deployment in progress. Please wait...") with EspError

