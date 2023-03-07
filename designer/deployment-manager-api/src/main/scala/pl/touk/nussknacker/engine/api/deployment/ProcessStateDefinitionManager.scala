package pl.touk.nussknacker.engine.api.deployment

import io.circe.Json
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.deployment.ProcessActionType.ProcessActionType
import pl.touk.nussknacker.engine.deployment.ExternalDeploymentId

import java.net.URI

//@TODO: In future clean up it.
/**
  * Provides state definitions, state transitions and status property transformations for UI visualization.
  */
trait ProcessStateDefinitionManager {

  /**
    * Default set of state definitions that provides state defaults
    * and allows filtering by fixed set of status IDs (see [[ProcessState]] and [[StateStatus.name]]).
    */
  def stateDefinitions(): Set[StateDefinition]

  /**
    * Status properties that describe how the state is transformed in order to be displayed in UI for each scenario.
    * Here the default values are based on stateDefinitions().
    * Override those methods to customize varying state properties or custom visualizations,
    * e.g. handle schedule date in [[PeriodicProcessStateDefinitionManager]]
    */
  def statusTooltip(stateStatus: StateStatus): Option[String] =
    stateDefinitions().toMapByName(stateStatus.name).tooltip

  def statusDescription(stateStatus: StateStatus): Option[String] =
    stateDefinitions().toMapByName(stateStatus.name).description

  def statusIcon(stateStatus: StateStatus): Option[URI] =
    stateDefinitions().toMapByName(stateStatus.name).icon

  /**
    * Allowed transitions between states.
    */
  def statusActions(stateStatus: StateStatus): List[ProcessActionType]
  //Temporary mapping ProcessActionType to StateStatus. TODO: Remove it when we will support state cache
  def mapActionToStatus(stateAction: Option[ProcessActionType]): StateStatus

  /**
    * Enhances raw [[StateStatus]] with scenario properties, including deployment info.
    */
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