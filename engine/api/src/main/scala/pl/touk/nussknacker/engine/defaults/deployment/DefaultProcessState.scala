package pl.touk.nussknacker.engine.defaults.deployment

import io.circe.Json
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.deployment.{DeploymentId, ProcessState}
import pl.touk.nussknacker.engine.api.deployment.StatusState.StateStatus

object DefaultProcessState {
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
      allowedActions = DefaultStateCustomConfigurator.getStatusActions(status),
      startTime = startTime,
      attributes = attributes,
      errorMessage = errorMessage
    )
}
