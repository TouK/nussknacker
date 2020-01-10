package pl.touk.nussknacker.engine.api.deployment.simple

import io.circe.Json
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.deployment.StateStatus
import pl.touk.nussknacker.engine.api.deployment.{DeploymentId, ProcessState}

object SimpleProcessState {
  def apply(deploymentId: DeploymentId,
            status: StateStatus,
            version: Option[ProcessVersion] = Option.empty,
            startTime: Option[Long] = Option.empty,
            attributes: Option[Json] = Option.empty,
            errorMessage: Option[String] = Option.empty): ProcessState =
    ProcessState(
      deploymentId = deploymentId,
      status = status,
      version = version,
      allowedActions = SimpleProcessStateDefinitionManager.statusActions(status),
      icon = SimpleProcessStateDefinitionManager.statusIcon(status),
      tooltip = SimpleProcessStateDefinitionManager.statusTooltip(status),
      startTime = startTime,
      attributes = attributes,
      errorMessage = errorMessage
    )
}
