package pl.touk.nussknacker.engine.management

import java.net.URI

import pl.touk.nussknacker.engine.api.deployment.StateAction.StateAction
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleProcessStateDefinitionManager
import pl.touk.nussknacker.engine.api.deployment.{ProcessStateDefinitionManager, StateAction, StateStatus}

object FlinkProcessStateDefinitionManager extends ProcessStateDefinitionManager  {
  val statusActionsMap: Map[StateStatus, List[StateAction]] = Map(
    FlinkStateStatus.Restarting -> List(StateAction.Cancel)
  )

  val statusIconsMap: Map[StateStatus, String] = Map(
    FlinkStateStatus.Restarting -> "/flink/states/deploy-restart-animated.svg"
  )

  val statusTooltipsMap: Map[StateStatus, String] = Map(
    FlinkStateStatus.Restarting -> "The process is restarting.."
  )

  override def statusTooltip(stateStatus: StateStatus): Option[String] =
    statusTooltipsMap.get(stateStatus).orElse(SimpleProcessStateDefinitionManager.statusTooltip(stateStatus))

  override def statusIcon(stateStatus: StateStatus): Option[URI] =
    statusIconsMap.get(stateStatus).map(URI.create).orElse(SimpleProcessStateDefinitionManager.statusIcon(stateStatus))

  override def statusActions(stateStatus: StateStatus): List[StateAction] =
    statusActionsMap.getOrElse(stateStatus, SimpleProcessStateDefinitionManager.statusActions(stateStatus))
}
