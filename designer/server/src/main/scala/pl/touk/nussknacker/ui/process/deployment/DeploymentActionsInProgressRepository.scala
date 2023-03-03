package pl.touk.nussknacker.ui.process.deployment

import pl.touk.nussknacker.restmodel.process.ProcessIdWithName
import pl.touk.nussknacker.ui.process.deployment.DeploymentActionsInProgressRepository.DeploymentActionId
import pl.touk.nussknacker.ui.security.api.LoggedUser

import scala.concurrent.Future

trait DeploymentActionsInProgressRepository {

  def addDeploymentActionInProgress(id: ProcessIdWithName, actionType: DeploymentActionType)
                                   (implicit user: LoggedUser): Future[DeploymentActionId]

  def removedDeploymentActionInProgress(id: ProcessIdWithName, actionId: DeploymentActionId): Future[Unit]

  def getDeploymentActionInProgressTypes(id: ProcessIdWithName): Future[Set[DeploymentActionType]]

}

object DeploymentActionsInProgressRepository {

  type DeploymentActionId = AnyRef

}

sealed trait DeploymentActionType

object DeploymentActionType {
  case object Deployment extends DeploymentActionType
  case object Cancel extends DeploymentActionType
}