package pl.touk.nussknacker.ui.process.exception

import pl.touk.nussknacker.engine.api.deployment.ProcessActionType.ProcessActionType
import pl.touk.nussknacker.engine.api.deployment.ProcessState
import pl.touk.nussknacker.restmodel.process.ProcessIdWithName
import pl.touk.nussknacker.ui.IllegalOperationError

case class ProcessIllegalAction(message: String) extends Exception(message) with IllegalOperationError

object ProcessIllegalAction {
  def apply(action: ProcessActionType, processIdWithName: ProcessIdWithName, state: ProcessState): ProcessIllegalAction =
    ProcessIllegalAction(s"Action: $action is not allowed in process (${processIdWithName.name.value}) state: ${state.status.name}, allowed actions: ${state.allowedActions.mkString(",")}.")

  def archived(action: ProcessActionType, processIdWithName: ProcessIdWithName): ProcessIllegalAction =
    ProcessIllegalAction(s"Forbidden action: $action for archived process: ${processIdWithName.name.value}.")

  def subprocess(action: ProcessActionType, processIdWithName: ProcessIdWithName): ProcessIllegalAction =
    ProcessIllegalAction(s"Forbidden action: $action for subproces: ${processIdWithName.name.value}.")
}