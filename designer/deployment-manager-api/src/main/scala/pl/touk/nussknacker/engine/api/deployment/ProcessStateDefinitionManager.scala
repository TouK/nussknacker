package pl.touk.nussknacker.engine.api.deployment

import pl.touk.nussknacker.engine.api.deployment.ScenarioActionName
import pl.touk.nussknacker.engine.api.deployment.StateStatus.StatusName

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
  def statusTooltip(stateStatus: StateStatus): String =
    stateDefinitions(stateStatus.name).tooltip

  def statusDescription(stateStatus: StateStatus): String =
    stateDefinitions(stateStatus.name).description

  def statusIcon(stateStatus: StateStatus): URI =
    stateDefinitions(stateStatus.name).icon

  /**
    * Allowed transitions between states.
    */
  def statusActions(stateStatus: StateStatus): List[ScenarioActionName]

  /**
    * Enhances raw [[StateStatus]] with scenario properties, including deployment info.
    */
  def processState(statusDetails: StatusDetails): ProcessState = {
    ProcessState(
      statusDetails.externalDeploymentId,
      statusDetails.status,
      statusDetails.version,
      statusActions(statusDetails.status),
      statusIcon(statusDetails.status),
      statusTooltip(statusDetails.status),
      statusDescription(statusDetails.status),
      statusDetails.startTime,
      statusDetails.attributes,
      statusDetails.errors
    )
  }

}
