package pl.touk.nussknacker.engine.api.deployment

import pl.touk.nussknacker.engine.api.deployment.ProcessActionType.ProcessActionType
import pl.touk.nussknacker.engine.api.deployment.StateStatus.StatusName
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleProcessStateDefinitionManager

import java.net.URI

/**
  * Wrapper for delegate [[ProcessStateDefinitionManager]], used to enhance base state definitions and actions
  * with custom states and custom actions. Default delegate is [[SimpleProcessStateDefinitionManager]]).
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
class OverridingProcessStateDefinitionManager(statusActionsPF: PartialFunction[StateStatus, List[ProcessActionType]] = PartialFunction.empty,
                                              mapActionToStatusPF: PartialFunction[Option[ProcessActionType], StateStatus] = PartialFunction.empty,
                                              statusIconsPF: PartialFunction[StateStatus, Option[URI]] = PartialFunction.empty,
                                              statusTooltipsPF: PartialFunction[StateStatus, Option[String]] = PartialFunction.empty,
                                              statusDescriptionsPF: PartialFunction[StateStatus, Option[String]] = PartialFunction.empty,
                                              customStateDefinitions: Map[StatusName, StateDefinitionDetails] = Map.empty,
                                              delegate: ProcessStateDefinitionManager = SimpleProcessStateDefinitionManager)
  extends ProcessStateDefinitionManager {

  override def statusActions(stateStatus: StateStatus): List[ProcessActionType] =
    statusActionsPF.applyOrElse(stateStatus, delegate.statusActions)

  override def statusIcon(stateStatus: StateStatus): Option[URI] =
    statusIconsPF.orElse(stateDefinitionsPF(_.icon)).applyOrElse(stateStatus, delegate.statusIcon)

  override def statusTooltip(stateStatus: StateStatus): Option[String] =
    statusTooltipsPF.orElse(stateDefinitionsPF(_.tooltip)).applyOrElse(stateStatus, delegate.statusTooltip)

  override def statusDescription(stateStatus: StateStatus): Option[String] =
    statusDescriptionsPF.orElse(stateDefinitionsPF(_.description)).applyOrElse(stateStatus, delegate.statusDescription)

  override def stateDefinitions: Map[StatusName, StateDefinitionDetails] =
    delegate.stateDefinitions ++ customStateDefinitions

  private def stateDefinitionsPF[T](map: StateDefinitionDetails => Option[T]): PartialFunction[StateStatus, Option[T]] = {
    case stateStatus if stateDefinitions.contains(stateStatus.name) => stateDefinitions.get(stateStatus.name).flatMap(map)
  }

}
