package pl.touk.nussknacker.engine.api.deployment

import pl.touk.nussknacker.engine.api.deployment.ProcessActionType.ProcessActionType
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleProcessStateDefinitionManager

import java.net.URI

/**
  * Wrapper for delegate [[ProcessStateDefinitionManager]], used to enhance base state definitions and actions
  * with custom states and actions (default delegate is [[SimpleProcessStateDefinitionManager]]).
  * Use statusIconsPF, statusTooltipsPF and statusDescriptionsPF to customize varying state properties.
  * The order of handlers:
  * <li>handle state via statusIconsPF, statusTooltipsPF and statusDescriptionsPF or else
  * <li>use custom default definitions from stateDefinitions or else
  * <li>use delegate
  * @param stateDefinitions Set of definitions that extends or overwrites delegate definitions
  */
class OverridingProcessStateDefinitionManager(statusActionsPF: PartialFunction[StateStatus, List[ProcessActionType]] = PartialFunction.empty,
                                              mapActionToStatusPF: PartialFunction[Option[ProcessActionType], StateStatus] = PartialFunction.empty,
                                              statusIconsPF: PartialFunction[StateStatus, Option[URI]] = PartialFunction.empty,
                                              statusTooltipsPF: PartialFunction[StateStatus, Option[String]] = PartialFunction.empty,
                                              statusDescriptionsPF: PartialFunction[StateStatus, Option[String]] = PartialFunction.empty,
                                              stateDefinitions: Set[StateDefinition] = Set.empty,
                                              delegate: ProcessStateDefinitionManager = SimpleProcessStateDefinitionManager)
  extends ProcessStateDefinitionManager {

  override def statusActions(stateStatus: StateStatus): List[ProcessActionType] =
    statusActionsPF.applyOrElse(stateStatus, delegate.statusActions)

  override def mapActionToStatus(stateAction: Option[ProcessActionType]): StateStatus =
    mapActionToStatusPF.applyOrElse(stateAction, delegate.mapActionToStatus)

  override def statusIcon(stateStatus: StateStatus): Option[URI] =
    statusIconsPF.orElse(customDefinitionPF(_.icon)).applyOrElse(stateStatus, delegate.statusIcon)

  override def statusTooltip(stateStatus: StateStatus): Option[String] =
    statusTooltipsPF.orElse(customDefinitionPF(_.tooltip)).applyOrElse(stateStatus, delegate.statusTooltip)

  override def statusDescription(stateStatus: StateStatus): Option[String] =
    statusDescriptionsPF.orElse(customDefinitionPF(_.description)).applyOrElse(stateStatus, delegate.statusDescription)

  override def stateDefinitions(): Set[StateDefinition] =
    delegate.stateDefinitions() ++ stateDefinitions

  private def customDefinitionPF[T](map: StateDefinition => Option[T]): PartialFunction[StateStatus, Option[T]] = {
    case stateStatus if stateDefinitions.toMapByName.contains(stateStatus.name) => stateDefinitions.toMapByName.get(stateStatus.name).flatMap(map)
  }

}
