package pl.touk.nussknacker.engine.api.deployment
import pl.touk.nussknacker.engine.api.deployment.StateAction.StateAction
import pl.touk.nussknacker.engine.api.deployment.StateStatus.StateStatus

object ProcessStateCustoms {
  val defaultActions = List(StateAction.Deploy)
  val defaultIcon = "/static/assets/states/status-unknown.svg"
  val defaultTooltip = "Unknown state of process."

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

  def getStatusActions(status: StateStatus, default: List[StateAction] = defaultActions): List[StateAction] =
    statusActions.getOrElse(status, defaultActions)

  val statusIcons: Map[StateStatus, String] = Map(
    StateStatus.Unknown -> "/static/assets/states/status-unknown.svg",
    StateStatus.NotDeployed -> "/static/assets/states/not-deployed.svg",
    StateStatus.DuringDeploy -> "/static/assets/states/deploy-running.svg",
    StateStatus.Running -> "/static/assets/states/running.svg",
    StateStatus.Canceled -> "/static/assets/states/stopping.svg",
    StateStatus.Restarting -> "/static/assets/states/stopped-working.svg",
    StateStatus.Failed -> "/static/assets/states/failed.svg",
    StateStatus.Finished -> "/static/assets/states/deploy-success.svg"
  )

  def getStatusIcon(status: StateStatus, default: String = defaultIcon): String =
    statusIcons.getOrElse(status, default)

  val statusTooltipMessages: Map[StateStatus, String] = Map(
    StateStatus.Unknown -> "Unknown state of process.",
    StateStatus.NotDeployed -> "Process has been never deployed.",
    StateStatus.DuringDeploy -> "Process has been already started and currently is during deploying.",
    StateStatus.Running -> "Process successful deployed and currently is running.",
    StateStatus.Canceled -> "Process is canceled.",
    StateStatus.Restarting -> "Process is restarting...",
    StateStatus.Failed -> "There are some problems with process..",
    StateStatus.Finished -> "Process completed successfully."
  )

  def getStatusTooltipMessage(status: StateStatus, default: String = defaultTooltip): String =
    statusTooltipMessages.getOrElse(status, default)
}