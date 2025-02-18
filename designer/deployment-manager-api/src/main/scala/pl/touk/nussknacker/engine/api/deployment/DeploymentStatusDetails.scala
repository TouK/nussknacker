package pl.touk.nussknacker.engine.api.deployment

import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.deployment.DeploymentId

case class DeploymentStatusDetails(
    status: StateStatus,
    // deploymentId is optional because some deployment managers (k8s) don't support it
    deploymentId: Option[DeploymentId],
    // version might be unavailable in some failing cases
    version: Option[ProcessVersion],
) {

  def deploymentIdUnsafe: DeploymentId =
    deploymentId.getOrElse(throw new IllegalStateException(s"deploymentId is missing"))

}
