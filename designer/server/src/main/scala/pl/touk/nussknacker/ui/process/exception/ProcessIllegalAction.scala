package pl.touk.nussknacker.ui.process.exception

import pl.touk.nussknacker.engine.api.deployment.ProcessActionType.ProcessActionType
import pl.touk.nussknacker.engine.api.deployment.ProcessState
import pl.touk.nussknacker.engine.api.process.{ProcessIdWithName, ProcessName}
import pl.touk.nussknacker.ui.IllegalOperationError

final case class ProcessIllegalAction(message: String) extends IllegalOperationError(message, details = "")

object ProcessIllegalAction {

  def apply(
      action: ProcessActionType,
      processName: ProcessName,
      state: ProcessState
  ): ProcessIllegalAction =
    ProcessIllegalAction(
      s"Action: $action is not allowed in scenario ($processName) state: ${state.status.name}, allowed actions: ${state.allowedActions
          .mkString(",")}."
    )

  def archived(action: ProcessActionType, processName: ProcessName): ProcessIllegalAction =
    ProcessIllegalAction(s"Forbidden action: $action for archived scenario: $processName.")

  def fragment(action: ProcessActionType, processName: ProcessName): ProcessIllegalAction =
    ProcessIllegalAction(s"Forbidden action: $action for fragment: $processName.")

}
