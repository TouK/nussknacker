package pl.touk.nussknacker.engine.management

import java.net.URI

import pl.touk.nussknacker.engine.api.deployment.ProcessActionType.ProcessActionType
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleProcessStateDefinitionManager
import pl.touk.nussknacker.engine.api.deployment.{ProcessStateDefinitionManager, ProcessActionType, StateStatus}

object FlinkProcessStateDefinitionManager extends ProcessStateDefinitionManager  {
  val statusActionsMap: Map[StateStatus, List[ProcessActionType]] = Map(
    FlinkStateStatus.Restarting -> List(ProcessActionType.Cancel)
  )

  val statusIconsMap: Map[StateStatus, String] = Map(
    FlinkStateStatus.Restarting -> "/flink/states/deploy-restart-animated.svg"
  )

  val statusTooltipsMap: Map[StateStatus, String] = Map(
    FlinkStateStatus.Restarting -> "The process is restarting.."
  )

  val statusMessagesMap: Map[StateStatus, String] = Map(
    FlinkStateStatus.Restarting -> "The process is restarting.."
  )

  override def statusTooltip(stateStatus: StateStatus): Option[String] =
    statusTooltipsMap.get(stateStatus).orElse(SimpleProcessStateDefinitionManager.statusTooltip(stateStatus))

  override def statusIcon(stateStatus: StateStatus): Option[URI] =
    statusIconsMap.get(stateStatus).map(URI.create).orElse(SimpleProcessStateDefinitionManager.statusIcon(stateStatus))

  override def statusActions(stateStatus: StateStatus): List[ProcessActionType] =
    statusActionsMap.getOrElse(stateStatus, SimpleProcessStateDefinitionManager.statusActions(stateStatus))

  override def mapActionToStatus(stateAction: Option[ProcessActionType]): StateStatus =
    SimpleProcessStateDefinitionManager.mapActionToStatus(stateAction)

  override def statusMessage(stateStatus: StateStatus): Option[String] =
    statusMessagesMap.get(stateStatus).orElse(SimpleProcessStateDefinitionManager.statusMessage(stateStatus))

  override def statusName(stateStatus: StateStatus): String =
    SimpleProcessStateDefinitionManager.statusName(stateStatus)
}
