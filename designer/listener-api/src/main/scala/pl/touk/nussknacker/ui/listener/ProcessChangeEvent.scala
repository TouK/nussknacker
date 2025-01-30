package pl.touk.nussknacker.ui.listener

import pl.touk.nussknacker.engine.api.Comment
import pl.touk.nussknacker.engine.api.deployment.ProcessActionId
import pl.touk.nussknacker.engine.api.deployment.ScenarioActionName
import pl.touk.nussknacker.engine.api.process.{ProcessId, ProcessName, VersionId}

import java.time.Instant

sealed trait ProcessChangeEvent {
  val processId: ProcessId
}

object ProcessChangeEvent {

  // Designer related events
  final case class OnSaved(processId: ProcessId, version: VersionId, isFragment: Boolean) extends ProcessChangeEvent
  final case class OnRenamed(processId: ProcessId, oldName: ProcessName, newName: ProcessName)
      extends ProcessChangeEvent
  final case class OnArchived(processId: ProcessId)   extends ProcessChangeEvent
  final case class OnUnarchived(processId: ProcessId) extends ProcessChangeEvent
  final case class OnDeleted(processId: ProcessId)    extends ProcessChangeEvent

  // Command and Action related events
  // TODO: use all command/action properties (not only deploymentComment)
  final case class OnActionSuccess(
      processId: ProcessId,
      version: VersionId,
      deploymentComment: Option[Comment],
      deployedAt: Instant,
      actionName: ScenarioActionName
  ) extends ProcessChangeEvent

  final case class OnActionFailed(processId: ProcessId, reason: Throwable, actionName: ScenarioActionName)
      extends ProcessChangeEvent

  // Periodic deployment events
  final case class OnActionExecutionFinished(actionId: ProcessActionId, processId: ProcessId, version: VersionId)
      extends ProcessChangeEvent
}
