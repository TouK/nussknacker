package pl.touk.nussknacker.ui.process.exception

import pl.touk.nussknacker.engine.api.deployment.ProcessActionType.ProcessActionType
import pl.touk.nussknacker.engine.api.deployment.ProcessState
import pl.touk.nussknacker.engine.api.process.ProcessIdWithName
import pl.touk.nussknacker.ui.IllegalOperationError

final case class ProcessIllegalAction(message: String) extends IllegalOperationError(message, details = "")

object ProcessIllegalAction {

  def apply(
      action: ProcessActionType,
      processIdWithName: ProcessIdWithName,
      state: ProcessState
  ): ProcessIllegalAction =
    ProcessIllegalAction(
      s"Action: $action is not allowed in scenario (${processIdWithName.name.value}) state: ${state.status.name}, allowed actions: ${state.allowedActions
          .mkString(",")}."
    )

  def archived(action: ProcessActionType, processIdWithName: ProcessIdWithName): ProcessIllegalAction =
    ProcessIllegalAction(s"Forbidden action: $action for archived scenario: ${processIdWithName.name.value}.")

  def fragment(action: ProcessActionType, processIdWithName: ProcessIdWithName): ProcessIllegalAction =
    ProcessIllegalAction(s"Forbidden action: $action for fragment: ${processIdWithName.name.value}.")

}
