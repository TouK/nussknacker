package pl.touk.nussknacker.engine.api.deployment.simple

import pl.touk.nussknacker.engine.api.deployment.{
  DeploymentStatus,
  DeploymentStatusName,
  NoAttributesDeploymentStatus,
  ProblemDeploymentStatus
}

object SimpleDeploymentStatus {

  object Problem {

    private val DefaultDescription = "There are some problems with deployment."

    val Failed: ProblemDeploymentStatus = ProblemDeploymentStatus(DefaultDescription)

  }

  val DuringDeploy: DeploymentStatus = NoAttributesDeploymentStatus(DeploymentStatusName("DURING_DEPLOY"))
  val Running: DeploymentStatus      = NoAttributesDeploymentStatus(DeploymentStatusName("RUNNING"))
  val Finished: DeploymentStatus     = NoAttributesDeploymentStatus(DeploymentStatusName("FINISHED"))
  val Restarting: DeploymentStatus   = NoAttributesDeploymentStatus(DeploymentStatusName("RESTARTING"))
  val DuringCancel: DeploymentStatus = NoAttributesDeploymentStatus(DeploymentStatusName("DURING_CANCEL"))
  val Canceled: DeploymentStatus     = NoAttributesDeploymentStatus(DeploymentStatusName("CANCELED"))

}
