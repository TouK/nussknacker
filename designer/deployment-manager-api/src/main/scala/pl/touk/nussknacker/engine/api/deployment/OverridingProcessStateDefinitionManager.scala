package pl.touk.nussknacker.engine.api.deployment

import pl.touk.nussknacker.engine.api.deployment.ProcessActionType.ProcessActionType
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleProcessStateDefinitionManager

import java.net.URI

class OverridingProcessStateDefinitionManager(statusActionsPF: PartialFunction[StateStatus, List[ProcessActionType]] = PartialFunction.empty,
                                              statusIconsPF: PartialFunction[StateStatus, String] = PartialFunction.empty,
                                              statusTooltipsPF: PartialFunction[StateStatus, String] = PartialFunction.empty,
                                              statusDescriptionsPF: PartialFunction[StateStatus, String] = PartialFunction.empty) extends ProcessStateDefinitionManager {

  override def statusTooltip(stateStatus: StateStatus): Option[String] =
    statusTooltipsPF.lift(stateStatus).orElse(SimpleProcessStateDefinitionManager.statusTooltip(stateStatus))

  override def statusIcon(stateStatus: StateStatus): Option[URI] =
    statusIconsPF.lift(stateStatus).map(URI.create).orElse(SimpleProcessStateDefinitionManager.statusIcon(stateStatus))

  override def statusActions(stateStatus: StateStatus): List[ProcessActionType] =
    statusActionsPF.applyOrElse(stateStatus, SimpleProcessStateDefinitionManager.statusActions)

  override def mapActionToStatus(stateAction: Option[ProcessActionType]): StateStatus =
    SimpleProcessStateDefinitionManager.mapActionToStatus(stateAction)

  override def statusDescription(stateStatus: StateStatus): Option[String] =
    statusDescriptionsPF.lift(stateStatus).orElse(SimpleProcessStateDefinitionManager.statusDescription(stateStatus))
}
