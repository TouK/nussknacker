package pl.touk.nussknacker.engine.api.deployment

import io.circe.Json
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.deployment.{DeploymentId, ExternalDeploymentId}

case class StatusDetails(
    status: StateStatus,
    deploymentId: Option[DeploymentId],
    externalDeploymentId: Option[ExternalDeploymentId] = None,
    version: Option[ProcessVersion] = None,
    startTime: Option[Long] = None,
    attributes: Option[Json] = None,
    errors: List[String] = List.empty
) {
  def externalDeploymentIdUnsafe: ExternalDeploymentId =
    externalDeploymentId.getOrElse(throw new IllegalStateException(s"externalDeploymentId is missing"))
}
