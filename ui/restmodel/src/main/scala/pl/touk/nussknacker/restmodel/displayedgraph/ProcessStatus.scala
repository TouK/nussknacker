package pl.touk.nussknacker.restmodel.displayedgraph

import java.net.URI
import pl.touk.nussknacker.engine.api.deployment.simple.{SimpleProcessStateDefinitionManager, SimpleStateStatus}
import pl.touk.nussknacker.engine.api.deployment.{ProcessState, ProcessStateDefinitionManager, StateStatus}
import pl.touk.nussknacker.engine.api.process.VersionId

object ProcessStatus {

  def simple(status: StateStatus): ProcessState =
    createState(status, SimpleProcessStateDefinitionManager)

  def simple(status: StateStatus, icon: Option[URI], tooltip: Option[String], description: Option[String], previousState: Option[ProcessState]): ProcessState =
    new ProcessState(
      version = previousState.flatMap(_.version),
      status = status,
      deploymentId = previousState.flatMap(_.deploymentId),
      allowedActions = SimpleProcessStateDefinitionManager.statusActions(status),
      icon = if (icon.isDefined) icon else SimpleProcessStateDefinitionManager.statusIcon(status),
      tooltip = if (tooltip.isDefined) tooltip else SimpleProcessStateDefinitionManager.statusTooltip(status),
      description = if (description.isDefined) description else SimpleProcessStateDefinitionManager.statusDescription(status),
      startTime = previousState.flatMap(_.startTime),
      attributes = previousState.flatMap(_.attributes),
      errors = previousState.map(_.errors).getOrElse(List.empty)
    )

  def createState(status: StateStatus,
                  processStateDefinitionManager: ProcessStateDefinitionManager): ProcessState =
    ProcessState(
      None,
      status,
      None,
      allowedActions = processStateDefinitionManager.statusActions(status),
      icon = processStateDefinitionManager.statusIcon(status),
      tooltip = processStateDefinitionManager.statusTooltip(status),
      description = processStateDefinitionManager.statusDescription(status),
      None,
      None,
      Nil
    )

  def simpleErrorShouldBeRunning(deployedVersionId: VersionId, user: String, previousState: Option[ProcessState]): ProcessState = simple(
    status = SimpleStateStatus.Error,
    icon = Some(SimpleProcessStateDefinitionManager.deployFailedIcon),
    tooltip = Some(SimpleProcessStateDefinitionManager.shouldBeRunningTooltip(deployedVersionId.value, user)),
    description = Some(SimpleProcessStateDefinitionManager.shouldBeRunningDescription),
    previousState = previousState
  )

  def simpleErrorMismatchDeployedVersion(deployedVersionId: VersionId, exceptedVersionId: VersionId, user: String, previousState: Option[ProcessState]): ProcessState = simple(
    status = SimpleStateStatus.Error,
    icon = Some(SimpleProcessStateDefinitionManager.deployFailedIcon),
    tooltip = Some(SimpleProcessStateDefinitionManager.mismatchDeployedVersionTooltip(deployedVersionId.value, exceptedVersionId.value, user)),
    description = Some(SimpleProcessStateDefinitionManager.mismatchDeployedVersionDescription),
    previousState = previousState
  )

  def simpleWarningShouldNotBeRunning(previousState: Option[ProcessState], deployed: Boolean): ProcessState = simple(
    status = SimpleStateStatus.Warning,
    icon = Some(SimpleProcessStateDefinitionManager.shouldNotBeRunningIcon(deployed)),
    tooltip = Some(SimpleProcessStateDefinitionManager.shouldNotBeRunningMessage(deployed)),
    description = Some(SimpleProcessStateDefinitionManager.shouldNotBeRunningMessage(deployed)),
    previousState = previousState
  )

  def simpleWarningMissingDeployedVersion(exceptedVersionId: VersionId, user: String, previousState: Option[ProcessState]): ProcessState = simple(
    status = SimpleStateStatus.Warning,
    icon = Some(SimpleProcessStateDefinitionManager.deployWarningIcon),
    tooltip = Some(SimpleProcessStateDefinitionManager.missingDeployedVersionTooltip(exceptedVersionId.value, user)),
    description = Some(SimpleProcessStateDefinitionManager.missingDeployedVersionDescription),
    previousState = previousState
  )

  def simpleWarningProcessWithoutAction(previousState: Option[ProcessState]): ProcessState = simple(
    status = SimpleStateStatus.Warning,
    icon = Some(SimpleProcessStateDefinitionManager.notDeployedWarningIcon),
    tooltip = Some(SimpleProcessStateDefinitionManager.processWithoutActionMessage),
    description = Some(SimpleProcessStateDefinitionManager.processWithoutActionMessage),
    previousState = previousState
  )

  val unknown: ProcessState = simple(SimpleStateStatus.Unknown)

  val failedToGet: ProcessState = simple(SimpleStateStatus.FailedToGet)
}
