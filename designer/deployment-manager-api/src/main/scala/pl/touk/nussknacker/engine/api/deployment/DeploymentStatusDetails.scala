package pl.touk.nussknacker.engine.api.deployment

import pl.touk.nussknacker.engine.api.process.VersionId
import pl.touk.nussknacker.engine.deployment.DeploymentId

case class DeploymentStatusDetails(
    status: StateStatus,
    // deploymentId is optional because some deployment managers (k8s) don't support it
    deploymentId: Option[DeploymentId],
    // version might be unavailable in some failing cases. It is used during checking if deployed version is the same
    // as expected by user - see InconsistentStateDetector.
    // TODO it should be an attribute of "following deploy" StateStatuses: DuringDeploy, Running  and Finished
    version: Option[VersionId],
) {

  def deploymentIdUnsafe: DeploymentId =
    deploymentId.getOrElse(throw new IllegalStateException(s"deploymentId is missing"))

}
