package pl.touk.nussknacker.engine.api.deployment
import pl.touk.nussknacker.engine.api.deployment.StateAction.StateAction
import pl.touk.nussknacker.engine.api.deployment.StateStatus.StateStatus

object ProcessStateCustomConfigurator extends ProcessStateConfigurator {
  val defaultActions = List(StateAction.Deploy)

  val statusActions: Map[StateStatus, List[StateAction]] = Map(
    StateStatus.Unknown -> List(StateAction.Deploy),
    StateStatus.NotDeployed -> List(StateAction.Deploy),
    StateStatus.DuringDeploy -> List(StateAction.Cancel),
    StateStatus.Running -> List(StateAction.Cancel, StateAction.Pause),
    StateStatus.Canceled -> List(StateAction.Deploy),
    StateStatus.Restarting -> List(StateAction.Cancel),
    StateStatus.Failed -> List(StateAction.Deploy),
    StateStatus.Finished -> List(StateAction.Deploy)
  )

  override def getStatusActions(status: StateStatus): List[StateAction] =
    statusActions.getOrElse(status, defaultActions)

  override def statusMessages: Option[Map[StateStatus, String]] = Option.empty
  override def statusIcons: Option[Map[StateStatus, String]] = Option.empty
}
