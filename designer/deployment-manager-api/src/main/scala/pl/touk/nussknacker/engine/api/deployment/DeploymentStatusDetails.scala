package pl.touk.nussknacker.engine.api.deployment

import pl.touk.nussknacker.engine.deployment.DeploymentId

// TODO replace by simple tuple DeploymentId -> DeploymentStatus after fixing TODOs
case class DeploymentStatusDetails(
    status: StateStatus,
    // deploymentId is optional because some deployment managers (k8s) don't support it
    deploymentId: Option[DeploymentId],
) {

  def deploymentIdUnsafe: DeploymentId =
    deploymentId.getOrElse(throw new IllegalStateException(s"deploymentId is missing"))

}
