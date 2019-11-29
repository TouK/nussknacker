package pl.touk.nussknacker.ui.listener

import java.time.LocalDateTime

import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.restmodel.process.ProcessId
import pl.touk.nussknacker.restmodel.processdetails.DeploymentAction.DeploymentAction

sealed trait ProcessChangeEvent {
  val processId: ProcessId
}

object ProcessChangeEvent {
  final case class OnArchived(processId: ProcessId) extends ProcessChangeEvent
  final case class OnCategoryChanged(processId: ProcessId, oldCategory: String, newCategory: String) extends ProcessChangeEvent
  final case class OnDeleted(processId: ProcessId) extends ProcessChangeEvent
  final case class OnDeployActionSuccess(processId: ProcessId, version: Long, comment: Option[String], deployedAt: LocalDateTime, action: DeploymentAction) extends ProcessChangeEvent
  final case class OnDeployActionFailed(processId: ProcessId, reason: Throwable) extends ProcessChangeEvent
  final case class OnRenamed(processId: ProcessId, oldName: ProcessName, newName: ProcessName) extends ProcessChangeEvent
  final case class OnSaved(processId: ProcessId, version: Long) extends ProcessChangeEvent
  final case class OnUnarchived(processId: ProcessId) extends ProcessChangeEvent
}