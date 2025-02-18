package pl.touk.nussknacker.engine.api.deployment

import pl.touk.nussknacker.engine.api.deployment.ProcessStateDefinitionManager.{
  DefaultVisibleActions,
  ScenarioStatusPresentationDetails,
  ScenarioStatusWithScenarioContext
}
import pl.touk.nussknacker.engine.api.deployment.StateStatus.StatusName
import pl.touk.nussknacker.engine.api.process.VersionId

import java.net.URI

// TODO: Some cleanups such as rename to sth close to presentation
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
  def statusTooltip(input: ScenarioStatusWithScenarioContext): String =
    stateDefinitions(input.scenarioStatus.name).tooltip

  def statusDescription(input: ScenarioStatusWithScenarioContext): String =
    stateDefinitions(input.scenarioStatus.name).description

  def statusIcon(input: ScenarioStatusWithScenarioContext): URI =
    statusIcon(input.scenarioStatus)

  private[nussknacker] def statusIcon(status: StateStatus): URI =
    stateDefinitions(status.name).icon

  /**
   * Actions that are applicable to scenario in general. They may be available only in particular states, as defined by `def statusActions`
   */
  def visibleActions(input: ScenarioStatusWithScenarioContext): List[ScenarioActionName] = DefaultVisibleActions

  /**
   * Custom tooltips for actions
   */
  def actionTooltips(input: ScenarioStatusWithScenarioContext): Map[ScenarioActionName, String] = Map.empty

  /**
    * Allowed transitions between states.
    */
  def statusActions(input: ScenarioStatusWithScenarioContext): Set[ScenarioActionName]

  /**
    * Returns presentations details of status
    */
  def statusPresentation(input: ScenarioStatusWithScenarioContext): ScenarioStatusPresentationDetails = {
    ScenarioStatusPresentationDetails(
      visibleActions(input),
      statusActions(input),
      actionTooltips(input),
      statusIcon(input),
      statusTooltip(input),
      statusDescription(input),
    )
  }

}

object ProcessStateDefinitionManager {

  /**
   * ScenarioStatusWithScenarioContext contains status of the scenario, as context of its based on DB state.
   * It is used as an argument of ProcessStateDefinitionManager methods
   *
   * @param scenarioStatus    current scenario state
   * @param latestVersionId   latest saved versionId for the scenario
   * @param deployedVersionId currently deployed versionId of the scenario
   */
  final case class ScenarioStatusWithScenarioContext(
      scenarioStatus: StateStatus,
      latestVersionId: VersionId,
      deployedVersionId: Option[VersionId],
      currentlyPresentedVersionId: Option[VersionId],
  )

  final case class ScenarioStatusPresentationDetails(
      visibleActions: List[ScenarioActionName],
      // This one is not exactly a part of presentation, it is rather a thing related with scenario lifecycle but for now it is kept here
      allowedActions: Set[ScenarioActionName],
      actionTooltips: Map[ScenarioActionName, String],
      icon: URI,
      tooltip: String,
      description: String
  )

  /**
   * Actions, that are applicable in standard use-cases for most deployment managers.
   */
  val DefaultVisibleActions: List[ScenarioActionName] = List(
    ScenarioActionName.Cancel,
    ScenarioActionName.Deploy,
    ScenarioActionName.Pause,
    ScenarioActionName.Archive,
    ScenarioActionName.UnArchive,
    ScenarioActionName.Rename,
  )

}
