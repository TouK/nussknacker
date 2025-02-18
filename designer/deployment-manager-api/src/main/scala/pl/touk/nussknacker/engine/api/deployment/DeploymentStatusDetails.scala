package pl.touk.nussknacker.engine.api.deployment

import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.deployment.{DeploymentId, ExternalDeploymentId}

case class DeploymentStatusDetails(
    status: StateStatus,
    // It is optional because some deployment managers (k8s) don't support it
    deploymentId: Option[DeploymentId],
    // TODO: This field is used only internally in FlinkDeploymentManager - it should be removed from the interface
    // In most cases it holds exactly the same information as deploymentId -
    // the only difference is scheduling mechanism where deploymentId holds Long and can't be used as Flink's JobID
    externalDeploymentId: Option[ExternalDeploymentId] = None,
    version: Option[ProcessVersion] = None,
    // TODO: This field is used only internally in FlinkDeploymentManager - it should be removed from the interface
    startTime: Option[Long] = None,
) {

  def deploymentIdUnsafe: DeploymentId =
    deploymentId.getOrElse(throw new IllegalStateException(s"deploymentId is missing"))

  def externalDeploymentIdUnsafe: ExternalDeploymentId =
    externalDeploymentId.getOrElse(throw new IllegalStateException(s"externalDeploymentId is missing"))

}
