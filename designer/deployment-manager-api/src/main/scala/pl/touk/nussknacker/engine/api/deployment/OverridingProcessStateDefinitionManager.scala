package pl.touk.nussknacker.engine.api.deployment

import pl.touk.nussknacker.engine.api.deployment.ProcessActionType.ProcessActionType
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
class OverridingProcessStateDefinitionManager(delegate: ProcessStateDefinitionManager,
                                              statusActionsPF: PartialFunction[StateStatus, List[ProcessActionType]] = PartialFunction.empty,
                                              statusIconsPF: PartialFunction[StateStatus, URI] = PartialFunction.empty,
                                              statusTooltipsPF: PartialFunction[StateStatus, Option[String]] = PartialFunction.empty,
                                              statusDescriptionsPF: PartialFunction[StateStatus, Option[String]] = PartialFunction.empty,
                                              customStateDefinitions: Map[StatusName, StateDefinitionDetails] = Map.empty)
  extends ProcessStateDefinitionManager {

  override def statusActions(stateStatus: StateStatus): List[ProcessActionType] =
    statusActionsPF.applyOrElse(stateStatus, delegate.statusActions)

  override def statusIcon(stateStatus: StateStatus): URI =
    statusIconsPF.orElse(stateDefinitionIconPF)
      .applyOrElse(stateStatus, delegate.statusIcon)

  override def statusTooltip(stateStatus: StateStatus): Option[String] =
    statusTooltipsPF.orElse(stateDefinitionsPF(_.tooltip)).applyOrElse(stateStatus, delegate.statusTooltip)

  override def statusDescription(stateStatus: StateStatus): Option[String] =
    statusDescriptionsPF.orElse(stateDefinitionsPF(_.description)).applyOrElse(stateStatus, delegate.statusDescription)

  override def stateDefinitions: Map[StatusName, StateDefinitionDetails] =
    delegate.stateDefinitions ++ customStateDefinitions

  private def stateDefinitionIconPF: PartialFunction[StateStatus, URI] = {
    case stateStatus: StateStatus if customStateDefinitions.contains(stateStatus.name) => customStateDefinitions(stateStatus.name).icon
  }

  private def stateDefinitionsPF[T](map: StateDefinitionDetails => Option[T]): PartialFunction[StateStatus, Option[T]] = {
    case stateStatus if customStateDefinitions.contains(stateStatus.name) => customStateDefinitions.get(stateStatus.name).flatMap(map)
  }

}
