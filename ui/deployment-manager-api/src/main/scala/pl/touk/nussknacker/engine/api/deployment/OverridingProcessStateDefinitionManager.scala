package pl.touk.nussknacker.engine.api.deployment

import pl.touk.nussknacker.engine.api.deployment.ProcessActionType.ProcessActionType
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleProcessStateDefinitionManager

import java.net.URI

class OverridingProcessStateDefinitionManager(val statusActionsMap: Map[StateStatus, List[ProcessActionType]] = Map(),
                                              val statusIconsMap: Map[StateStatus, String] = Map(),
                                              val statusTooltipsMap: Map[StateStatus, String] = Map(),
                                              val statusDescriptionsMap: Map[StateStatus, String] = Map()) extends ProcessStateDefinitionManager {

  override def statusTooltip(stateStatus: StateStatus): Option[String] =
    statusTooltipsMap.get(stateStatus).orElse(SimpleProcessStateDefinitionManager.statusTooltip(stateStatus))

  override def statusIcon(stateStatus: StateStatus): Option[URI] =
    statusIconsMap.get(stateStatus).map(URI.create).orElse(SimpleProcessStateDefinitionManager.statusIcon(stateStatus))

  override def statusActions(stateStatus: StateStatus): List[ProcessActionType] =
    statusActionsMap.getOrElse(stateStatus, SimpleProcessStateDefinitionManager.statusActions(stateStatus))

  override def mapActionToStatus(stateAction: Option[ProcessActionType]): StateStatus =
    SimpleProcessStateDefinitionManager.mapActionToStatus(stateAction)

  override def statusDescription(stateStatus: StateStatus): Option[String] =
    statusDescriptionsMap.get(stateStatus).orElse(SimpleProcessStateDefinitionManager.statusDescription(stateStatus))
}
