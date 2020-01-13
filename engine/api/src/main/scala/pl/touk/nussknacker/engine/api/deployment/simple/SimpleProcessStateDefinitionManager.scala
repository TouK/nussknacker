package pl.touk.nussknacker.engine.api.deployment.simple

import java.net.URI

import pl.touk.nussknacker.engine.api.deployment.StateAction.StateAction
import pl.touk.nussknacker.engine.api.deployment.{ProcessStateDefinitionManager, StateAction, StateStatus}

object SimpleProcessStateDefinitionManager extends ProcessStateDefinitionManager {
  val defaultActions = List()

  val statusActionsMap: Map[StateStatus, List[StateAction]] = Map(
    SimpleStateStatus.Unknown -> List(StateAction.Deploy),
    SimpleStateStatus.NotDeployed -> List(StateAction.Deploy),
    SimpleStateStatus.DuringDeploy -> List(StateAction.Cancel),
    SimpleStateStatus.Running -> List(StateAction.Cancel, StateAction.Pause),
    SimpleStateStatus.Canceled -> List(StateAction.Deploy),
    SimpleStateStatus.Failed -> List(StateAction.Deploy),
    SimpleStateStatus.Finished -> List(StateAction.Deploy)
  )

  val statusIconsMap: Map[StateStatus, String] = Map(
    SimpleStateStatus.FailedToGet -> "/assets/states/error.svg",
    SimpleStateStatus.NotFound -> "/assets/states/process-does-not-exist.svg",
    SimpleStateStatus.Unknown -> "/assets/states/status-unknown.svg",
    SimpleStateStatus.NotDeployed -> "/assets/states/not-deployed.svg",
    SimpleStateStatus.DuringDeploy -> "/assets/states/deploy-running-animated.svg",
    SimpleStateStatus.Running -> "/assets/states/deploy-success.svg",
    SimpleStateStatus.Canceled -> "/assets/states/stopping-success.svg",
    SimpleStateStatus.DuringCancel -> "/assets/states/stopping-running-animated.svg",
    SimpleStateStatus.Failed -> "/assets/states/failed.svg",
    SimpleStateStatus.Finished -> "/assets/states/success.svg",
    SimpleStateStatus.Error -> "/assets/states/error.svg"
  )

  val statusTooltipsMap: Map[StateStatus, String] = Map(
    SimpleStateStatus.FailedToGet -> "There are some problems with obtaining process state at engine. Please check if your engine is working properly..",
    SimpleStateStatus.NotFound -> "There are some problems with process. Please check if process really exists..",
    SimpleStateStatus.Unknown -> "Unknown state of the process..",
    SimpleStateStatus.NotDeployed -> "The process has never been deployed.",
    SimpleStateStatus.DuringDeploy -> "The process has been already started and currently is being deployed.",
    SimpleStateStatus.Running -> "The process is running.",
    SimpleStateStatus.Canceled -> "The process has been successfully cancelled.",
    SimpleStateStatus.DuringCancel -> "The process currently is being canceled.",
    SimpleStateStatus.Failed -> "There are some problems with checking state of process..",
    SimpleStateStatus.Finished -> "The process completed successfully.",
    SimpleStateStatus.Error -> "There are some errors with process state. Please check if everything is okay with process."
  )

  override def statusTooltip(stateStatus: StateStatus): Option[String] =
    statusTooltipsMap.get(stateStatus)

  override def statusIcon(stateStatus: StateStatus): Option[URI] =
    statusIconsMap.get(stateStatus).map(URI.create)

  override def statusActions(stateStatus: StateStatus): List[StateAction] =
    statusActionsMap.getOrElse(stateStatus, defaultActions)
}
