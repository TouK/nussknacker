package pl.touk.nussknacker.engine.api.deployment

import io.circe.Json
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.deployment.ProcessActionType.ProcessActionType
import pl.touk.nussknacker.engine.api.deployment.StateStatus.StatusName
import pl.touk.nussknacker.engine.deployment.ExternalDeploymentId

import java.net.URI

//@TODO: In future clean up it.
/**
  * Used to specify status definitions (for filtering and scenario status visualization) and status transitions (actions).
  */
trait ProcessStateDefinitionManager {

  /**
    * Dictionary of state definitions with default properties.
    * Usages:
    * <ul>
    * <li>fixed set of filter options for scenario filtering by state.
    * <li>default values of status properties
    * </ul>
    * To handle dynamic state properties (e.g. descriptions) use statusDescription, statusTooltip or statusIcon.
    */
  def stateDefinitions: Map[StatusName, StateDefinitionDetails]

  /**
    * Status properties that describe how the state is transformed in order to be displayed in UI for each scenario.
    * Here the default values are based on stateDefinitions().
    * Override those methods to customize varying state properties or custom visualizations,
    * e.g. handle schedule date in [[PeriodicProcessStateDefinitionManager]]
    */
  def statusTooltip(stateStatus: StateStatus): Option[String] =
    stateDefinitions(stateStatus.name).tooltip

  def statusDescription(stateStatus: StateStatus): Option[String] =
    stateDefinitions(stateStatus.name).description

  def statusIcon(stateStatus: StateStatus): Option[URI] =
    stateDefinitions(stateStatus.name).icon

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