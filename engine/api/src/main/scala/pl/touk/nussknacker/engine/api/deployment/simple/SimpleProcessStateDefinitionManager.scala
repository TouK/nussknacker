package pl.touk.nussknacker.engine.api.deployment.simple

import java.net.URI

import pl.touk.nussknacker.engine.api.deployment.ProcessActionType.ProcessActionType
import pl.touk.nussknacker.engine.api.deployment.{ProcessActionType, ProcessStateDefinitionManager, StateStatus}

object SimpleProcessStateDefinitionManager extends ProcessStateDefinitionManager {
  val defaultActions = List()

  val actionStatusMap: Map[ProcessActionType, StateStatus] = Map(
    ProcessActionType.Deploy -> SimpleStateStatus.Running,
    ProcessActionType.Cancel -> SimpleStateStatus.Canceled
  )

  val statusActionsMap: Map[StateStatus, List[ProcessActionType]] = Map(
    SimpleStateStatus.Unknown -> List(ProcessActionType.Deploy),
    SimpleStateStatus.NotDeployed -> List(ProcessActionType.Deploy),
    SimpleStateStatus.DuringDeploy -> List(ProcessActionType.Cancel),
    SimpleStateStatus.Running -> List(ProcessActionType.Cancel, ProcessActionType.Pause),
    SimpleStateStatus.Canceled -> List(ProcessActionType.Deploy),
    SimpleStateStatus.Failed -> List(ProcessActionType.Deploy),
    SimpleStateStatus.Finished -> List(ProcessActionType.Deploy),
    SimpleStateStatus.Error -> List(ProcessActionType.Deploy)
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
    SimpleStateStatus.Unknown -> "Unknown state of the process.. We can't recognize process state..",
    SimpleStateStatus.NotDeployed -> "The process has never been deployed.",
    SimpleStateStatus.DuringDeploy -> "The process has been already started and currently is being deployed.",
    SimpleStateStatus.Running -> "The process has been successfully deployed and currently is running.",
    SimpleStateStatus.Canceled -> "The process has been successfully cancelled.",
    SimpleStateStatus.DuringCancel -> "The process currently is being canceled.",
    SimpleStateStatus.Failed -> "There are some problems with checking state of process..",
    SimpleStateStatus.Finished -> "The process completed successfully.",
    SimpleStateStatus.Error -> "There are some errors with process state. Please check if everything is okay with process!"
  )

  val statusDescriptionsMap: Map[StateStatus, String] = Map(
    SimpleStateStatus.FailedToGet -> "Failed to get state of process..",
    SimpleStateStatus.NotFound -> "Process state was not found..",
    SimpleStateStatus.Unknown -> "Unknown state of the process..",
    SimpleStateStatus.NotDeployed -> "Process has never been deployed.",
    SimpleStateStatus.DuringDeploy -> "Process is being deployed.",
    SimpleStateStatus.Running -> "Process currently is running.",
    SimpleStateStatus.Canceled -> "Process currently is canceled.",
    SimpleStateStatus.DuringCancel -> "Process is being canceled.",
    SimpleStateStatus.Failed -> "There are some problems with process..",
    SimpleStateStatus.Finished -> "Process has been successfully finished job.",
    SimpleStateStatus.Error -> "There are some errors with process!"
  )

  override def statusIcon(stateStatus: StateStatus): Option[URI] =
    statusIconsMap.get(stateStatus).map(URI.create)

  override def statusActions(stateStatus: StateStatus): List[ProcessActionType] =
    statusActionsMap.getOrElse(stateStatus, defaultActions)

  override def mapActionToStatus(stateAction: Option[ProcessActionType]): StateStatus =
    stateAction
      .map(sa => actionStatusMap.getOrElse(sa, SimpleStateStatus.Unknown))
      .getOrElse(SimpleStateStatus.NotDeployed)

  override def statusTooltip(stateStatus: StateStatus): Option[String] =
    statusTooltipsMap.get(stateStatus)

  def errorShouldRunningTooltip(deployedVersionId: Long, user: String): String =
    s"Process deployed in version ${deployedVersionId} (by ${user}), should be running!"

  def errorShouldNotBeRunningTooltip(deployedVersionId: Long, user: String): String =
    s"Process deployed in version ${deployedVersionId} (by ${user}), should not be running!"

  def errorMismatchDeployedVersionTooltip(deployedVersionId: Long, exceptedVersionId: Long, user: String): String =
    s"Process deployed in version ${deployedVersionId} (by ${user}), expected version ${exceptedVersionId}!"

  def errorMissingDeployedVersionTooltip(exceptedVersionId: Long, user: String): String =
    s"Process deployed without version (by ${user}), expected version ${exceptedVersionId}!"

  override def statusDescription(stateStatus: StateStatus): Option[String] =
    statusDescriptionsMap.get(stateStatus)

  def errorShouldRunningDescription: String = "Process currently is not running!"

  def errorShouldNotBeRunningDescription: String = "Process should not be running!"

  def errorMismatchDeployedVersionDescription: String = "Deployed process mismatch version!"

  def errorMissingDeployedVersionDescription: String = "Missing version of deployed process!"
}
