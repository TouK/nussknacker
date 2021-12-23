package pl.touk.nussknacker.streaming.embedded

import pl.touk.nussknacker.engine.api.deployment.ProcessActionType.ProcessActionType
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleProcessStateDefinitionManager
import pl.touk.nussknacker.engine.api.deployment.{ProcessActionType, ProcessStateDefinitionManager, StateStatus}

import java.net.URI

object EmbeddedProcessStateDefinitionManager extends ProcessStateDefinitionManager  {
  val statusActionsMap: Map[StateStatus, List[ProcessActionType]] = Map(
    EmbeddedStateStatus.Restarting -> List(ProcessActionType.Cancel)
  )

  val statusIconsMap: Map[StateStatus, String] = Map(
    EmbeddedStateStatus.Restarting -> "/assets/embedded/states/deploy-restart-animated.svg"
  )

  val statusTooltipsMap: Map[StateStatus, String] = Map(
    EmbeddedStateStatus.Restarting -> "Scenario was deployed but now is restarting..."
  )

  val statusDescriptionsMap: Map[StateStatus, String] = Map(
    EmbeddedStateStatus.Restarting -> "Scenario is restarting..."
  )

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
