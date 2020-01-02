package pl.touk.nussknacker.restmodel.displayedgraph

import io.circe.Json
import io.circe.generic.JsonCodec
import pl.touk.nussknacker.engine.api.deployment.StateAction.StateAction
import pl.touk.nussknacker.engine.api.deployment.StatusState.StateStatus
import pl.touk.nussknacker.engine.api.deployment.{ProcessState, StatusState}
import pl.touk.nussknacker.engine.defaults.deployment.{DefaultStateStatus, DefaultProcessStateConfigurator}

@JsonCodec case class ProcessStatus(deploymentId: Option[String],
                                    status: String,
                                    allowedActions: List[StateAction],
                                    startTime: Option[Long],
                                    attributes: Option[Json],
                                    errorMessage: Option[String])

object ProcessStatus {
  def apply(deploymentId: Option[String],
            status: StateStatus,
            allowedActions: List[StateAction],
            startTime: Option[Long] = Option.empty,
            attributes: Option[Json] = Option.empty,
            errorMessage: Option[String] = Option.empty) = new ProcessStatus(
    deploymentId = deploymentId,
    status = status.toString(),
    allowedActions = allowedActions,
    startTime = startTime,
    attributes = attributes,
    errorMessage = errorMessage
  )

  def apply(processState: ProcessState, expectedDeploymentVersion: Option[Long]): ProcessStatus = {
    val versionMatchMessage = (processState.version, expectedDeploymentVersion) match {
      //currently returning version is optional
      case (None, _) => None
      case (Some(stateVersion), Some(expectedVersion)) if stateVersion.versionId == expectedVersion => None
      case (Some(stateVersion), Some(expectedVersion)) => Some(s"Process deployed in version ${stateVersion.versionId} (by ${stateVersion.user}), expected version $expectedVersion")
      case (Some(stateVersion), None) => Some(s"Process deployed in version ${stateVersion.versionId} (by ${stateVersion.user}), should not be deployed")
    }

    ProcessStatus(
      deploymentId = Some(processState.deploymentId.value),
      status = processState.status,
      allowedActions = processState.allowedActions,
      startTime = processState.startTime,
      attributes = processState.attributes,
      errorMessage = List(versionMatchMessage, processState.errorMessage).flatten.reduceOption(_  + ", " + _)
    )
  }

  val notFound: ProcessStatus = ProcessStatus(
    None,
    DefaultStateStatus.Unknown,
    DefaultProcessStateConfigurator.getStatusActions(DefaultStateStatus.Unknown),
    errorMessage = Some("Process not found in engine.")
  )

  val failedToGet: ProcessStatus = ProcessStatus(
    None,
    DefaultStateStatus.Unknown,
    DefaultProcessStateConfigurator.getStatusActions(DefaultStateStatus.Unknown),
    errorMessage = Some("Failed to obtain status.")
  )
}
