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
    SimpleStateStatus.Running -> List(ProcessActionType.Cancel, ProcessActionType.Pause, ProcessActionType.Deploy),
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
    SimpleStateStatus.FailedToGet -> "There are problems obtaining the process state. Please check if your engine is working properly.",
    SimpleStateStatus.NotFound -> "Your engine is working but have no information about the process.",
    SimpleStateStatus.Unknown -> "Unknown state of the process. We can't recognize process state.",
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
    SimpleStateStatus.FailedToGet -> "Failed to get a state of the process.",
    SimpleStateStatus.NotFound -> "The process state was not found.",
    SimpleStateStatus.Unknown -> "Unknown state of the process.",
    SimpleStateStatus.NotDeployed -> "The process has never been deployed.",
    SimpleStateStatus.DuringDeploy -> "The process is being deployed.",
    SimpleStateStatus.Running -> "The process is running.",
    SimpleStateStatus.Canceled -> "The process is canceled.",
    SimpleStateStatus.DuringCancel -> "The process is being canceled.",
    SimpleStateStatus.Failed -> "There are problems with the process.",
    SimpleStateStatus.Finished -> "The process has finished.",
    SimpleStateStatus.Error -> "There are errors establishing a process state."
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
