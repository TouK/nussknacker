package pl.touk.nussknacker.engine.api.deployment.simple

import java.net.URI
import pl.touk.nussknacker.engine.api.deployment.ProcessActionType.ProcessActionType
import pl.touk.nussknacker.engine.api.deployment.{ProcessActionType, ProcessState, ProcessStateDefinitionManager, StateDefinition, StateStatus}
import pl.touk.nussknacker.engine.api.process.VersionId

object SimpleProcessStateDefinitionManager extends ProcessStateDefinitionManager {

  private val defaultActions: List[ProcessActionType] = Nil

  private val actionStatusMap: Map[ProcessActionType, StateStatus] = Map(
    ProcessActionType.Deploy -> SimpleStateStatus.Running,
    ProcessActionType.Cancel -> SimpleStateStatus.Canceled,
    ProcessActionType.Archive -> SimpleStateStatus.NotDeployed,
    ProcessActionType.UnArchive -> SimpleStateStatus.NotDeployed
  )

  private val statusActionsMap: Map[StateStatus, List[ProcessActionType]] = Map(
    SimpleStateStatus.Unknown -> List(ProcessActionType.Deploy),
    SimpleStateStatus.NotDeployed -> List(ProcessActionType.Deploy, ProcessActionType.Archive),
    SimpleStateStatus.DuringDeploy -> List(ProcessActionType.Deploy, ProcessActionType.Cancel), // Deploy? see FlinkStateStatus
    SimpleStateStatus.Running -> List(ProcessActionType.Cancel, ProcessActionType.Pause, ProcessActionType.Deploy),
    SimpleStateStatus.Canceled -> List(ProcessActionType.Deploy, ProcessActionType.Archive),
    SimpleStateStatus.Restarting -> List(ProcessActionType.Deploy, ProcessActionType.Cancel), // Deploy? see FlinkStateStatus
    SimpleStateStatus.Finished -> List(ProcessActionType.Deploy, ProcessActionType.Archive),
    // When Failed - process is in terminal state in Flink and it doesn't require any cleanup in Flink, but in NK it does
    // - that's why Cancel action is available
    SimpleStateStatus.Failed -> List(ProcessActionType.Deploy, ProcessActionType.Cancel),
    SimpleStateStatus.Error -> List(ProcessActionType.Deploy, ProcessActionType.Cancel),
    SimpleStateStatus.Warning -> List(ProcessActionType.Deploy, ProcessActionType.Cancel),
    SimpleStateStatus.FailedToGet -> List(ProcessActionType.Deploy, ProcessActionType.Archive)
  )

  override def statusActions(stateStatus: StateStatus): List[ProcessActionType] =
    statusActionsMap.getOrElse(stateStatus, defaultActions)

  override def mapActionToStatus(stateAction: Option[ProcessActionType]): StateStatus =
    stateAction
      .map(sa => actionStatusMap.getOrElse(sa, SimpleStateStatus.Unknown))
      .getOrElse(SimpleStateStatus.NotDeployed)

  override def stateDefinitions(): Set[StateDefinition] =
    SimpleStateStatus.definitions

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
