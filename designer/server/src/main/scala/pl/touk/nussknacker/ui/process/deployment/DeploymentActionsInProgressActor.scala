package pl.touk.nussknacker.ui.process.deployment

import akka.actor.{ActorRef, Props}
import akka.pattern.ask
import akka.util.Timeout
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.restmodel.process.ProcessIdWithName
import pl.touk.nussknacker.ui.process.deployment.DeploymentActionsInProgressActor._
import pl.touk.nussknacker.ui.process.deployment.DeploymentActionsInProgressRepository.DeploymentActionId
import pl.touk.nussknacker.ui.security.api.LoggedUser
import pl.touk.nussknacker.ui.util.FailurePropagatingActor

import scala.concurrent.{ExecutionContext, Future}

class DeploymentActionsInProgressActor extends FailurePropagatingActor with LazyLogging {

  private var deploymentActionsInProgress = Map[(ProcessName, DeploymentActionId), DeployInfo]()

  private implicit val ec: ExecutionContext = context.dispatcher

  override def receive: PartialFunction[Any, Unit] = {
    case AddDeploymentActionInProgress(id, user, actionType) =>
      val actionId = new Object
      deploymentActionsInProgress += (id.name, actionId) -> DeployInfo(user.username, actionType)
      sender() ! actionId
    case RemoveDeploymentActionInProgress(process, actionId) =>
      deploymentActionsInProgress -= ((process.name, actionId))
      sender() ! (())
    case GetDeploymentActionInProgressTypes(id) =>
      sender() ! deploymentActionTypesInProgress(id)
    case GetAllDeploymentActionsInProgress =>
      val deduplicatedActions = deploymentActionsInProgress.map {
        case ((processName, _), deployInfo) => processName -> deployInfo
      }
      sender() ! DeploymentActionsInProgress(deduplicatedActions)
  }

  private def deploymentActionTypesInProgress(idWithName: ProcessIdWithName): Set[DeploymentActionType] = {
    deploymentActionsInProgress.toList.collect {
      case ((processName, _), DeployInfo(_, actionType)) if processName == idWithName.name => actionType
    }.toSet
  }

}

object DeploymentActionsInProgressActor {

  private type DeploymentActionId = AnyRef

  def props: Props = {
    Props(new DeploymentActionsInProgressActor)
  }

  private trait DeploymentAction {
    def id: ProcessIdWithName
  }

  private case class RemoveDeploymentActionInProgress(processIdWithName: ProcessIdWithName, actionId: DeploymentActionId)

  private case object GetAllDeploymentActionsInProgress

  private case class AddDeploymentActionInProgress(id: ProcessIdWithName, user: LoggedUser, actionType: DeploymentActionType)

  private case class GetDeploymentActionInProgressTypes(id: ProcessIdWithName)

  class ActorBasedDeploymentActionsInProgressRepository(managerActor: ActorRef,
                                                        systemRequestTimeout: Timeout) extends DeploymentActionsInProgressRepository {

    private implicit val timeout: Timeout = systemRequestTimeout
    override def addDeploymentActionInProgress(id: ProcessIdWithName, actionType: DeploymentActionType)
                                              (implicit user: LoggedUser): Future[DeploymentActionId] = {
      (managerActor ? AddDeploymentActionInProgress(id, user, actionType)).mapTo[DeploymentActionId]
    }

    override def removedDeploymentActionInProgress(id: ProcessIdWithName, actionId: DeploymentActionId): Future[Unit] = {
      (managerActor ? RemoveDeploymentActionInProgress(id, actionId)).mapTo[Unit]
    }

    override def getDeploymentActionInProgressTypes(id: ProcessIdWithName): Future[Set[DeploymentActionType]] = {
      (managerActor ? GetDeploymentActionInProgressTypes(id)).mapTo[Set[DeploymentActionType]]
    }

    override def getAllDeploymentActionsInProgress: Future[DeploymentActionsInProgress] = {
      (managerActor ? GetAllDeploymentActionsInProgress).mapTo[DeploymentActionsInProgress]
    }

  }

}


