package pl.touk.nussknacker.listner

import pl.touk.nussknacker.engine.api.process.ProcessName

sealed trait ChangeEvent

object ChangeEvent {
  final case class OnArchived(processName: ProcessName) extends ChangeEvent
  final case class OnCategoryChanged(processName: ProcessName, category: String) extends ChangeEvent
  final case class OnDeleted(processName: ProcessName) extends ChangeEvent
  final case class OnDeploySuccess(processName: ProcessName) extends ChangeEvent
  final case class OnDeployCancelled(processName: ProcessName) extends ChangeEvent
  final case class OnDeployFailed(processName: ProcessName) extends ChangeEvent
  final case class OnRenamed(oldName: ProcessName, newName: ProcessName) extends ChangeEvent
  final case class OnSaved(processName: ProcessName) extends ChangeEvent
  final case class OnUnarchived(processName: ProcessName) extends ChangeEvent
}