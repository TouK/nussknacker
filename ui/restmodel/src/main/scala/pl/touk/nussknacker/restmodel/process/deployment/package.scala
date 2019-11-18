package pl.touk.nussknacker.restmodel.process

package object deployment {
  case class DeployInfo(userId: String, time: Long, action: DeploymentActionType)

  sealed trait DeploymentActionType

  object DeploymentActionType {
    case object Deployment extends DeploymentActionType
    case object Cancel extends DeploymentActionType
  }
}
