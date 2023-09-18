package pl.touk.nussknacker.ui.process.exception

import pl.touk.nussknacker.engine.api.deployment.ProcessState
import pl.touk.nussknacker.engine.api.process.ProcessIdWithName
import pl.touk.nussknacker.ui.ForbiddenError

final case class ProcessIllegalAction(message: String) extends Exception(message) with ForbiddenError

object ProcessIllegalAction {

  def apply(
      actionName: String,
      processIdWithName: ProcessIdWithName,
      state: ProcessState
  ): ProcessIllegalAction =
    ProcessIllegalAction(
      s"Action: $actionName is not allowed in scenario (${processIdWithName.name.value}) state: ${state.status.name}, allowed actions: ${state.allowedActions
          .mkString(",")}."
    )

  def archived(actionName: String, processIdWithName: ProcessIdWithName): ProcessIllegalAction =
    ProcessIllegalAction(s"Forbidden action: $actionName for archived scenario: ${processIdWithName.name.value}.")

  def fragment(actionName: String, processIdWithName: ProcessIdWithName): ProcessIllegalAction =
    ProcessIllegalAction(s"Forbidden action: $actionName for fragment: ${processIdWithName.name.value}.")

}
