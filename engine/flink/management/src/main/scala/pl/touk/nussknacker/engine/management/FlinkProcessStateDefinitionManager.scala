package pl.touk.nussknacker.engine.management

import java.net.URI
import pl.touk.nussknacker.engine.api.deployment.ProcessActionType.ProcessActionType
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleProcessStateDefinitionManager
import pl.touk.nussknacker.engine.api.deployment.{CustomAction, ProcessActionType, ProcessStateDefinitionManager, StateStatus}

object FlinkProcessStateDefinitionManager extends ProcessStateDefinitionManager  {
  val statusActionsMap: Map[StateStatus, List[ProcessActionType]] = Map(
    FlinkStateStatus.Restarting -> List(ProcessActionType.Cancel)
  )

  val statusIconsMap: Map[StateStatus, String] = Map(
    FlinkStateStatus.Restarting -> "/assets/flink/states/deploy-restart-animated.svg"
  )

  val statusTooltipsMap: Map[StateStatus, String] = Map(
    FlinkStateStatus.Restarting -> "Process was deployed but now is restarting..."
  )

  val statusDescriptionsMap: Map[StateStatus, String] = Map(
    FlinkStateStatus.Restarting -> "Process is restarting..."
  )

  override def statusTooltip(stateStatus: StateStatus): Option[String] =
    statusTooltipsMap.get(stateStatus).orElse(SimpleProcessStateDefinitionManager.statusTooltip(stateStatus))

  override def statusIcon(stateStatus: StateStatus): Option[URI] =
    statusIconsMap.get(stateStatus).map(URI.create).orElse(SimpleProcessStateDefinitionManager.statusIcon(stateStatus))

  override def statusActions(stateStatus: StateStatus): List[ProcessActionType] =
    statusActionsMap.getOrElse(stateStatus, SimpleProcessStateDefinitionManager.statusActions(stateStatus))

  override val customActions: List[CustomAction] = List.empty

  override def mapActionToStatus(stateAction: Option[ProcessActionType]): StateStatus =
    SimpleProcessStateDefinitionManager.mapActionToStatus(stateAction)

  override def statusDescription(stateStatus: StateStatus): Option[String] =
    statusDescriptionsMap.get(stateStatus).orElse(SimpleProcessStateDefinitionManager.statusDescription(stateStatus))
}
