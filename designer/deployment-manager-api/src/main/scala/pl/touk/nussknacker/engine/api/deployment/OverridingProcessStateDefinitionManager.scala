package pl.touk.nussknacker.engine.api.deployment

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
    statusActionsPF: PartialFunction[StateStatus, List[ScenarioActionName]] = PartialFunction.empty,
    statusIconsPF: PartialFunction[StateStatus, URI] = PartialFunction.empty,
    statusTooltipsPF: PartialFunction[StateStatus, String] = PartialFunction.empty,
    statusDescriptionsPF: PartialFunction[StateStatus, String] = PartialFunction.empty,
    customStateDefinitions: Map[StatusName, StateDefinitionDetails] = Map.empty
) extends ProcessStateDefinitionManager {

  override def statusActions(stateStatus: StateStatus): List[ScenarioActionName] =
    statusActionsPF.applyOrElse(stateStatus, delegate.statusActions)

  override def statusIcon(stateStatus: StateStatus): URI =
    statusIconsPF.orElse(stateDefinitionsPF(_.icon)).applyOrElse(stateStatus, delegate.statusIcon)

  override def statusTooltip(stateStatus: StateStatus): String =
    statusTooltipsPF.orElse(stateDefinitionsPF(_.tooltip)).applyOrElse(stateStatus, delegate.statusTooltip)

  override def statusDescription(stateStatus: StateStatus): String =
    statusDescriptionsPF.orElse(stateDefinitionsPF(_.description)).applyOrElse(stateStatus, delegate.statusDescription)

  override def stateDefinitions: Map[StatusName, StateDefinitionDetails] =
    delegate.stateDefinitions ++ customStateDefinitions

  private def stateDefinitionsPF[T](map: StateDefinitionDetails => T): PartialFunction[StateStatus, T] = {
    case stateStatus if customStateDefinitions.contains(stateStatus.name) =>
      map(customStateDefinitions(stateStatus.name))
  }

}
