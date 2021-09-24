package pl.touk.nussknacker.ui.listener

import java.time.LocalDateTime
import pl.touk.nussknacker.engine.api.deployment.ProcessActionType.ProcessActionType
import pl.touk.nussknacker.engine.api.process.{ProcessId, ProcessName, VersionId}

sealed trait ProcessChangeEvent {
  val processId: ProcessId
}

object ProcessChangeEvent {
  final case class OnArchived(processId: ProcessId) extends ProcessChangeEvent
  final case class OnCategoryChanged(processId: ProcessId, oldCategory: String, newCategory: String) extends ProcessChangeEvent
  final case class OnDeleted(processId: ProcessId) extends ProcessChangeEvent
  final case class OnDeployActionSuccess(processId: ProcessId, version: VersionId, comment: Option[String], deployedAt: LocalDateTime, action: ProcessActionType) extends ProcessChangeEvent
  final case class OnDeployActionFailed(processId: ProcessId, reason: Throwable) extends ProcessChangeEvent
  final case class OnRenamed(processId: ProcessId, oldName: ProcessName, newName: ProcessName) extends ProcessChangeEvent
  final case class OnSaved(processId: ProcessId, version: VersionId) extends ProcessChangeEvent
  final case class OnFinished(processId: ProcessId, version: VersionId) extends ProcessChangeEvent
  final case class OnUnarchived(processId: ProcessId) extends ProcessChangeEvent
}
