package pl.touk.nussknacker.engine.api.deployment

import io.circe.Json
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.deployment.ProcessActionType.ProcessActionType
import pl.touk.nussknacker.engine.deployment.ExternalDeploymentId

import java.net.URI

//@TODO: In future clean up it.
trait ProcessStateDefinitionManager {
  def statusActions(stateStatus: StateStatus): List[ProcessActionType]
  def statusTooltip(stateStatus: StateStatus): Option[String]
  def statusDescription(stateStatus: StateStatus): Option[String]
  def statusIcon(stateStatus: StateStatus): Option[URI]
  //Temporary mapping ProcessActionType to StateStatus. TODO: Remove it when we will support state cache
  def mapActionToStatus(stateAction: Option[ProcessActionType]): StateStatus

  def processState(status: StateStatus,
                   deploymentId: Option[ExternalDeploymentId] = None,
                   version: Option[ProcessVersion] = None,
                   startTime: Option[Long] = None,
                   attributes: Option[Json] = None,
                   errors: List[String] = List.empty): ProcessState = {
    ProcessState(
      deploymentId,
      status,
      version,
      statusActions(status),
      statusIcon(status),
      statusTooltip(status),
      statusDescription(status),
      startTime,
      attributes,
      errors)
  }

}