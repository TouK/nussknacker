package pl.touk.nussknacker.engine.api.deployment

import pl.touk.nussknacker.engine.api.deployment.ProcessStateDefinitionManager.{ProcessStatus, defaultApplicableActions}
import pl.touk.nussknacker.engine.api.deployment.StateStatus.StatusName
import pl.touk.nussknacker.engine.api.process.VersionId

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
   * Actions that are applicable to scenario in general. They may be available only in particular states, as defined by `def statusActions`
   */
  def applicableActions: List[ScenarioActionName] = defaultApplicableActions

  /**
   * Custom tooltips for actions
   */
  def actionTooltips(processStatus: ProcessStatus): Map[ScenarioActionName, String] = Map.empty

  /**
    * Allowed transitions between states.
    */
  def statusActions(processStatus: ProcessStatus): List[ScenarioActionName]

  /**
    * Enhances raw [[StateStatus]] with scenario properties, including deployment info.
    */
  def processState(
      statusDetails: StatusDetails,
      latestVersionId: VersionId,
      deployedVersionId: Option[VersionId]
  ): ProcessState = {
    val status = ProcessStatus(statusDetails.status, latestVersionId, deployedVersionId)
    ProcessState(
      statusDetails.externalDeploymentId,
      statusDetails.status,
      statusDetails.version,
      applicableActions,
      statusActions(status),
      actionTooltips(status),
      statusIcon(statusDetails.status),
      statusTooltip(statusDetails.status),
      statusDescription(statusDetails.status),
      statusDetails.startTime,
      statusDetails.attributes,
      statusDetails.errors
    )
  }

}

object ProcessStateDefinitionManager {

  /**
   * ProcessStatus contains status of the scenario, it is used as argument of ProcessStateDefinitionManager methods
   *
   * @param stateStatus       current scenario state
   * @param latestVersionId   latest saved versionId for the scenario
   * @param deployedVersionId currently deployed versionId of the scenario
   */
  final case class ProcessStatus(
      stateStatus: StateStatus,
      latestVersionId: VersionId,
      deployedVersionId: Option[VersionId]
  )

  /**
   * Actions, that are applicable in standard use-cases for most deployment managers.
   */
  val defaultApplicableActions: List[ScenarioActionName] = List(
    ScenarioActionName.Cancel,
    ScenarioActionName.Deploy,
    ScenarioActionName.Pause,
    ScenarioActionName.Archive,
    ScenarioActionName.UnArchive,
    ScenarioActionName.Rename,
  )

}
