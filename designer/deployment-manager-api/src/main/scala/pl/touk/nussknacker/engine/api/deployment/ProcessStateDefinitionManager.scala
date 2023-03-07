package pl.touk.nussknacker.engine.api.deployment

import io.circe.Json
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.deployment.ProcessActionType.ProcessActionType
import pl.touk.nussknacker.engine.deployment.ExternalDeploymentId

import java.net.URI

//@TODO: In future clean up it.
trait ProcessStateDefinitionManager {

  //Default set of state definitions for filtering by static set of status IDs (aka name).
  def stateDefinitions(): Set[StateDefinition]

  //State properties that describe how state is displayed in UI for each scenario.
  //Default values are based on stateDefinitions. Override those methods to
  // customize state properties, e.g. handle varying properties of state, such as
  // schedule date in PeriodicProcessStateDefinitionManager
  def statusTooltip(stateStatus: StateStatus): Option[String] =
    stateDefinitions().toMapByName(stateStatus.name).tooltip

  def statusDescription(stateStatus: StateStatus): Option[String] =
    stateDefinitions().toMapByName(stateStatus.name).description

  def statusIcon(stateStatus: StateStatus): Option[URI] =
    stateDefinitions().toMapByName(stateStatus.name).icon

  def statusActions(stateStatus: StateStatus): List[ProcessActionType]
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