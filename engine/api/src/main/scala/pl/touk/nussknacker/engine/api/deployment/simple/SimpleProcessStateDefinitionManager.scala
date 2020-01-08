package pl.touk.nussknacker.engine.api.deployment.simple

import pl.touk.nussknacker.engine.api.deployment.StateAction.StateAction
import pl.touk.nussknacker.engine.api.deployment.StateStatus.StateStatus
import pl.touk.nussknacker.engine.api.deployment.{ProcessStateDefinitionManager, StateAction}

object SimpleProcessStateDefinitionManager extends ProcessStateDefinitionManager {
  val defaultActions = List()

  val statusActions: Map[StateStatus, List[StateAction]] = Map(
    SimpleStateStatus.Unknown -> List(StateAction.Deploy),
    SimpleStateStatus.NotDeployed -> List(StateAction.Deploy),
    SimpleStateStatus.DuringDeploy -> List(StateAction.Cancel),
    SimpleStateStatus.Running -> List(StateAction.Cancel, StateAction.Pause),
    SimpleStateStatus.Canceled -> List(StateAction.Deploy),
    SimpleStateStatus.Failed -> List(StateAction.Deploy),
    SimpleStateStatus.Finished -> List(StateAction.Deploy)
  )

  override def statusTooltips: Map[StateStatus, String] = Map.empty

  override def statusIcons: Map[StateStatus, String] = Map.empty

  override def getStatusActions(stateStatus: StateStatus): List[StateAction] =
    statusActions.getOrElse(stateStatus, defaultActions)
}
