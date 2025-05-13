package pl.touk.nussknacker.engine.api.deployment

import pl.touk.nussknacker.engine.api.deployment.StateStatus.StatusName

// This class represents both deployment status and scenario status.
// TODO: we should use DeploymentStatus in StatusDetails which is returned by DeploymentManager.getScenarioDeploymentsStatuses
//       but before we do this, we should move version to dedicated status and scenario scheduling mechanism should stop using DeploymentManager's API
trait StateStatus {
  // Status identifier, should be unique among all states registered within all processing types.
  def name: StatusName
}

object StateStatus {
  type StatusName = String

  // Temporary methods to simplify status creation
  def apply(statusName: StatusName): StateStatus = NoAttributesStateStatus(statusName)

}

case class NoAttributesStateStatus(name: StatusName) extends StateStatus {
  override def toString: String = name
}
