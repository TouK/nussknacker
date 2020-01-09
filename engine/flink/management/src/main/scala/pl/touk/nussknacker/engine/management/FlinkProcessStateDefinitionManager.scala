package pl.touk.nussknacker.engine.management

import java.net.URI

import pl.touk.nussknacker.engine.api.deployment.StateAction.StateAction
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleProcessStateDefinitionManager
import pl.touk.nussknacker.engine.api.deployment.{ProcessStateDefinitionManager, StateAction, StateStatus}

object FlinkProcessStateDefinitionManager extends ProcessStateDefinitionManager  {
  val statusActions: Map[StateStatus, List[StateAction]] = Map(
    FlinkStateStatus.Restarting -> List(StateAction.Cancel)
  )

  val statusIcons: Map[StateStatus, String] = Map(
    FlinkStateStatus.Restarting -> "/flink/states/deploy-restart-animated.svg"
  )

  val statusTooltips: Map[StateStatus, String] = Map(
    FlinkStateStatus.Restarting -> "The process is restarting.."
  )

  override def getStatusTooltip(stateStatus: StateStatus): Option[String] =
    statusTooltips.get(stateStatus).orElse(SimpleProcessStateDefinitionManager.getStatusTooltip(stateStatus))

  override def getStatusIcon(stateStatus: StateStatus): Option[URI] =
    statusIcons.get(stateStatus).map(URI.create).orElse(SimpleProcessStateDefinitionManager.getStatusIcon(stateStatus))

  override def getStatusActions(stateStatus: StateStatus): List[StateAction] =
    statusActions.getOrElse(stateStatus, SimpleProcessStateDefinitionManager.getStatusActions(stateStatus))
}
