package pl.touk.nussknacker.engine.customs.deployment

import pl.touk.nussknacker.engine.api.deployment.StateAction.StateAction
import pl.touk.nussknacker.engine.api.deployment.StatusState.StateStatus
import pl.touk.nussknacker.engine.api.deployment.{ProcessStateConfigurator, StateAction}

object ProcessStateCustomConfigurator extends ProcessStateConfigurator {
  val defaultActions = List(StateAction.Deploy)

  val statusActions: Map[StateStatus, List[StateAction]] = Map(
    CustomStateStatus.Unknown -> List(StateAction.Deploy),
    CustomStateStatus.NotDeployed -> List(StateAction.Deploy),
    CustomStateStatus.DuringDeploy -> List(StateAction.Cancel),
    CustomStateStatus.Running -> List(StateAction.Cancel, StateAction.Pause),
    CustomStateStatus.Canceled -> List(StateAction.Deploy),
    CustomStateStatus.Restarting -> List(StateAction.Cancel),
    CustomStateStatus.Failed -> List(StateAction.Deploy),
    CustomStateStatus.Finished -> List(StateAction.Deploy)
  )

  override def getStatusActions(status: StateStatus): List[StateAction] =
    statusActions.getOrElse(status, defaultActions)

  override def statusMessages: Option[Map[StateStatus, String]] = Option.empty
  override def statusIcons: Option[Map[StateStatus, String]] = Option.empty
}
