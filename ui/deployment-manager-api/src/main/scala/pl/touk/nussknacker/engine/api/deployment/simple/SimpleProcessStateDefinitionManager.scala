package pl.touk.nussknacker.engine.api.deployment.simple

import java.net.URI
import pl.touk.nussknacker.engine.api.deployment.ProcessActionType.ProcessActionType
import pl.touk.nussknacker.engine.api.deployment.{ProcessActionType, ProcessState, ProcessStateDefinitionManager, StateStatus}
import pl.touk.nussknacker.engine.api.process.VersionId

object SimpleProcessStateDefinitionManager extends ProcessStateDefinitionManager {
  val defaultActions = List()

  val actionStatusMap: Map[ProcessActionType, StateStatus] = Map(
    ProcessActionType.Deploy -> SimpleStateStatus.Running,
    ProcessActionType.Cancel -> SimpleStateStatus.Canceled,
    ProcessActionType.Archive -> SimpleStateStatus.NotDeployed,
    ProcessActionType.UnArchive -> SimpleStateStatus.NotDeployed
  )

  val statusActionsMap: Map[StateStatus, List[ProcessActionType]] = Map(
    SimpleStateStatus.Unknown -> List(ProcessActionType.Deploy),
    SimpleStateStatus.NotDeployed -> List(ProcessActionType.Deploy, ProcessActionType.Archive),
    SimpleStateStatus.DuringDeploy -> List(ProcessActionType.Cancel),
    SimpleStateStatus.Running -> List(ProcessActionType.Cancel, ProcessActionType.Pause, ProcessActionType.Deploy),
    SimpleStateStatus.Canceled -> List(ProcessActionType.Deploy, ProcessActionType.Archive),
    SimpleStateStatus.Finished -> List(ProcessActionType.Deploy, ProcessActionType.Archive),
    // When Failed - process is in terminal state in Flink and it doesn't require any cleanup in Flink, but in NK it does
    // - that's why Cancel action is available
    SimpleStateStatus.Failed -> List(ProcessActionType.Deploy, ProcessActionType.Cancel),
    SimpleStateStatus.Error -> List(ProcessActionType.Deploy, ProcessActionType.Cancel),
    SimpleStateStatus.Warning -> List(ProcessActionType.Deploy, ProcessActionType.Cancel),
    SimpleStateStatus.FailedToGet -> List(ProcessActionType.Deploy, ProcessActionType.Archive)
  )

  val statusIconsMap: Map[StateStatus, String] = Map(
    SimpleStateStatus.FailedToGet -> "/assets/states/error.svg",
    SimpleStateStatus.Unknown -> "/assets/states/status-unknown.svg",
    SimpleStateStatus.NotDeployed -> "/assets/states/not-deployed.svg",
    SimpleStateStatus.DuringDeploy -> "/assets/states/deploy-running-animated.svg",
    SimpleStateStatus.Running -> "/assets/states/deploy-success.svg",
    SimpleStateStatus.Canceled -> "/assets/states/stopping-success.svg",
    SimpleStateStatus.DuringCancel -> "/assets/states/stopping-running-animated.svg",
    SimpleStateStatus.Failed -> "/assets/states/failed.svg",
    SimpleStateStatus.Finished -> "/assets/states/success.svg",
    SimpleStateStatus.Error -> "/assets/states/error.svg",
    SimpleStateStatus.Warning -> "/assets/states/warning.svg"
  )

  val statusTooltipsMap: Map[StateStatus, String] = Map(
    SimpleStateStatus.FailedToGet -> "There are problems obtaining the scenario state. Please check if your engine is working properly.",
    SimpleStateStatus.Unknown -> "Unknown state of the scenario. We can't recognize scenario state.",
    SimpleStateStatus.NotDeployed -> "The scenario is not deployed.",
    SimpleStateStatus.DuringDeploy -> "The scenario has been already started and currently is being deployed.",
    SimpleStateStatus.Running -> "The scenario has been successfully deployed and currently is running.",
    SimpleStateStatus.Canceled -> "The scenario has been successfully cancelled.",
    SimpleStateStatus.DuringCancel -> "The scenario currently is being canceled.",
    SimpleStateStatus.Failed -> "There are some problems with scenario.",
    SimpleStateStatus.Finished -> "The scenario completed successfully.",
    SimpleStateStatus.Error -> "There are some errors. Please check if everything is okay with scenario!",
    SimpleStateStatus.Warning -> "There are some warnings. Please check if everything is okay with scenario!"
  )

  val statusDescriptionsMap: Map[StateStatus, String] = Map(
    SimpleStateStatus.FailedToGet -> "Failed to get a state of the scenario.",
    SimpleStateStatus.Unknown -> "Unknown state of the scenario.",
    SimpleStateStatus.NotDeployed -> "The scenario is not deployed.",
    SimpleStateStatus.DuringDeploy -> "The scenario is being deployed.",
    SimpleStateStatus.Running -> "The scenario is running.",
    SimpleStateStatus.Canceled -> "The scenario is canceled.",
    SimpleStateStatus.DuringCancel -> "The scenario is being canceled.",
    SimpleStateStatus.Failed -> "There are some problems with scenario.",
    SimpleStateStatus.Finished -> "The scenario has finished.",
    SimpleStateStatus.Error -> "There are errors establishing a scenario state.",
    SimpleStateStatus.Warning -> "There are some warnings establishing a scenario state."
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

  override def statusDescription(stateStatus: StateStatus): Option[String] =
    statusDescriptionsMap.get(stateStatus)

  def errorShouldBeRunningState(deployedVersionId: VersionId, user: String): ProcessState =
    processState(SimpleStateStatus.Error).copy(
      icon = Some(deployFailedIcon),
      tooltip = Some(shouldBeRunningTooltip(deployedVersionId.value, user)),
      description = Some(shouldBeRunningDescription))

  def errorMismatchDeployedVersionState(deployedVersionId: VersionId, exceptedVersionId: VersionId, user: String): ProcessState =
    processState(SimpleStateStatus.Error).copy(
      icon = Some(deployFailedIcon),
      tooltip = Some(mismatchDeployedVersionTooltip(deployedVersionId.value, exceptedVersionId.value, user)),
      description = Some(mismatchDeployedVersionDescription))

  def warningShouldNotBeRunningState(deployed: Boolean): ProcessState =
    processState(SimpleStateStatus.Warning).copy(
      icon = Some(shouldNotBeRunningIcon(deployed)),
      tooltip = Some(shouldNotBeRunningMessage(deployed)),
      description = Some(shouldNotBeRunningMessage(deployed)))

  def warningMissingDeployedVersionState(exceptedVersionId: VersionId, user: String): ProcessState =
    processState(SimpleStateStatus.Warning).copy(
      icon = Some(deployWarningIcon),
      tooltip = Some(missingDeployedVersionTooltip(exceptedVersionId.value, user)),
      description = Some(missingDeployedVersionDescription))

  lazy val warningProcessWithoutActionState: ProcessState =
    processState(SimpleStateStatus.Warning).copy(
      icon = Some(notDeployedWarningIcon),
      tooltip = Some(processWithoutActionMessage),
      description = Some(processWithoutActionMessage))
  
  def shouldBeRunningTooltip(deployedVersionId: Long, user: String): String =
    s"Scenario deployed in version ${deployedVersionId} (by ${user}), should be running!"

  def mismatchDeployedVersionTooltip(deployedVersionId: Long, exceptedVersionId: Long, user: String): String =
    s"Scenario deployed in version ${deployedVersionId} (by ${user}), expected version ${exceptedVersionId}!"

  def missingDeployedVersionTooltip(exceptedVersionId: Long, user: String): String =
    s"Scenario deployed without version (by ${user}), expected version ${exceptedVersionId}!"

  val shouldBeRunningDescription: String = "Scenario currently is not running!"

  val mismatchDeployedVersionDescription: String = "Deployed scenario mismatch version!"

  val missingDeployedVersionDescription: String = "Missing version of deployed scenario!"

  val processWithoutActionMessage: String = "Scenario state error - no actions found!"

  val deployFailedIcon: URI = URI.create("/assets/states/deploy-failed.svg")

  val deployWarningIcon: URI = URI.create("/assets/states/deploy-warning.svg")

  val stoppingWarningIcon: URI = URI.create("/assets/states/stopping-warning.svg")

  val notDeployedWarningIcon: URI = URI.create("/assets/states/not-deployed-warning.svg")

  def  shouldNotBeRunningMessage(deployed: Boolean): String =
    if (deployed) "Scenario has been canceled but still is running!"
    else "Scenario has been never deployed but now is running!"

  def shouldNotBeRunningIcon(deployed: Boolean): URI =
    if (deployed) stoppingWarningIcon else notDeployedWarningIcon
}
