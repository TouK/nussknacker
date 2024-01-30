package pl.touk.nussknacker.ui.listener

import pl.touk.nussknacker.engine.api.deployment.ProcessActionId
import pl.touk.nussknacker.engine.api.deployment.ProcessActionType.ProcessActionType
import pl.touk.nussknacker.engine.api.process.{ProcessId, ProcessName, VersionId}

import java.time.Instant

sealed trait ProcessChangeEvent {
  val processId: ProcessId
}

object ProcessChangeEvent {
  final case class OnArchived(processId: ProcessId) extends ProcessChangeEvent
  final case class OnDeleted(processId: ProcessId)  extends ProcessChangeEvent

  final case class OnDeployActionSuccess(
      processId: ProcessId,
      version: VersionId,
      deploymentComment: Option[Comment],
      deployedAt: Instant,
      action: ProcessActionType
  ) extends ProcessChangeEvent

  final case class OnDeployActionFailed(processId: ProcessId, reason: Throwable) extends ProcessChangeEvent
  final case class OnRenamed(processId: ProcessId, oldName: ProcessName, newName: ProcessName)
      extends ProcessChangeEvent
  final case class OnSaved(processId: ProcessId, version: VersionId) extends ProcessChangeEvent
  // Deprecated: OnFinished event is published only by legacy periodic processes which dont have actionId field filled
  // It should be removed as soon as we get rid of these legacy processes
  final case class OnFinished(processId: ProcessId, version: VersionId) extends ProcessChangeEvent
  final case class OnUnarchived(processId: ProcessId)                   extends ProcessChangeEvent
  final case class OnActionExecutionFinished(actionId: ProcessActionId, processId: ProcessId, version: VersionId)
      extends ProcessChangeEvent
}
