package pl.touk.nussknacker.engine.api.deployment.periodic.model

sealed trait PeriodicDeployStatus

object PeriodicDeployStatus {
  case object Scheduled      extends PeriodicDeployStatus
  case object Deployed       extends PeriodicDeployStatus
  case object Finished       extends PeriodicDeployStatus
  case object Failed         extends PeriodicDeployStatus
  case object RetryingDeploy extends PeriodicDeployStatus
  case object FailedOnDeploy extends PeriodicDeployStatus
}
