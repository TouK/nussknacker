package pl.touk.nussknacker.engine.management

import pl.touk.nussknacker.engine.api.deployment.StateAction.StateAction
import pl.touk.nussknacker.engine.api.deployment.StateStatus.StateStatus
import pl.touk.nussknacker.engine.api.deployment.{ProcessStateDefinitionManager, StateAction}

object FlinkProcessStateDefinitionManager extends ProcessStateDefinitionManager  {
  val defaultActions = List()

  val statusActions: Map[StateStatus, List[StateAction]] = Map(
    FlinkStateStatus.Unknown -> List(StateAction.Deploy),
    FlinkStateStatus.NotDeployed -> List(StateAction.Deploy),
    FlinkStateStatus.DuringDeploy -> List(StateAction.Cancel),
    FlinkStateStatus.Running -> List(StateAction.Cancel, StateAction.Pause),
    FlinkStateStatus.Restarting -> List(StateAction.Cancel, StateAction.Pause),
    FlinkStateStatus.DuringCancel -> List(),
    FlinkStateStatus.Canceled -> List(StateAction.Deploy),
    FlinkStateStatus.Failed -> List(StateAction.Deploy),
    FlinkStateStatus.Finished -> List(StateAction.Deploy)
  )

  override def statusTooltips: Map[StateStatus, String] = Map.empty

  override def statusIcons: Map[StateStatus, String] = Map.empty

  override def getStatusActions(stateStatus: StateStatus): List[StateAction] =
    statusActions.getOrElse(stateStatus, defaultActions)
}
