package pl.touk.nussknacker.engine.api.deployment

import pl.touk.nussknacker.engine.api.deployment.ProcessStateDefinitionManager.ScenarioStatusWithScenarioContext
import pl.touk.nussknacker.engine.api.deployment.StateStatus.StatusName

import java.net.URI

/**
  * Wrapper for delegate [[ProcessStateDefinitionManager]], used to enhance base state definitions and actions
  * with custom states and custom actions.
  *
  * Use statusIconsPF, statusTooltipsPF and statusDescriptionsPF to customize dynamic state properties.
  * Use customStateDefinitions to extend or override delegate definitions.
  *
  * The order of overriding handler executions:
  * <li>handle state via statusIconsPF, statusTooltipsPF and statusDescriptionsPF or else
  * <li>use custom definitions from stateDefinitions or else
  * <li>use delegate methods statusIcons, statusTooltips and statusDescriptions
  *
  * @param customStateDefinitions Set of definitions that extends or overwrites delegate definitions
  */
class OverridingProcessStateDefinitionManager(
    delegate: ProcessStateDefinitionManager,
    statusActionsPF: PartialFunction[ScenarioStatusWithScenarioContext, Set[ScenarioActionName]] =
      PartialFunction.empty,
    statusIconsPF: PartialFunction[StateStatus, URI] = PartialFunction.empty,
    statusTooltipsPF: PartialFunction[StateStatus, String] = PartialFunction.empty,
    statusDescriptionsPF: PartialFunction[StateStatus, String] = PartialFunction.empty,
    customStateDefinitions: Map[StatusName, StateDefinitionDetails] = Map.empty,
    customVisibleActions: Option[List[ScenarioActionName]] = None,
    customActionTooltips: Option[ScenarioStatusWithScenarioContext => Map[ScenarioActionName, String]] = None,
) extends ProcessStateDefinitionManager {

  override def visibleActions(input: ScenarioStatusWithScenarioContext): List[ScenarioActionName] =
    customVisibleActions.getOrElse(delegate.visibleActions(input))

  override def statusActions(input: ScenarioStatusWithScenarioContext): Set[ScenarioActionName] =
    statusActionsPF.applyOrElse(input, delegate.statusActions)

  override def actionTooltips(input: ScenarioStatusWithScenarioContext): Map[ScenarioActionName, String] =
    customActionTooltips.map(_(input)).getOrElse(delegate.actionTooltips(input))

  override def statusIcon(input: ScenarioStatusWithScenarioContext): URI =
    statusIconsPF
      .orElse(stateDefinitionsPF(_.icon))
      .lift(input.scenarioStatus)
      .getOrElse(delegate.statusIcon(input))

  override def statusTooltip(input: ScenarioStatusWithScenarioContext): String =
    statusTooltipsPF
      .orElse(stateDefinitionsPF(_.tooltip))
      .lift(input.scenarioStatus)
      .getOrElse(delegate.statusTooltip(input))

  override def statusDescription(input: ScenarioStatusWithScenarioContext): String =
    statusDescriptionsPF
      .orElse(stateDefinitionsPF(_.description))
      .lift(input.scenarioStatus)
      .getOrElse(delegate.statusDescription(input))

  override def stateDefinitions: Map[StatusName, StateDefinitionDetails] =
    delegate.stateDefinitions ++ customStateDefinitions

  private def stateDefinitionsPF[T](map: StateDefinitionDetails => T): PartialFunction[StateStatus, T] = {
    case stateStatus if customStateDefinitions.contains(stateStatus.name) =>
      map(customStateDefinitions(stateStatus.name))
  }

}
