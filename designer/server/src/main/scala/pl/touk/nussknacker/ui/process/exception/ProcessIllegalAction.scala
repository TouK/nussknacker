package pl.touk.nussknacker.ui.process.exception

import pl.touk.nussknacker.engine.api.deployment.{ScenarioActionName, StateStatus}
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.restmodel.scenariodetails.ScenarioStatusDto
import pl.touk.nussknacker.ui.IllegalOperationError

final case class ProcessIllegalAction(message: String) extends IllegalOperationError(message, details = "")

object ProcessIllegalAction {

  def apply(
      actionName: ScenarioActionName,
      processName: ProcessName,
      state: ScenarioStatusDto
  ): ProcessIllegalAction =
    apply(actionName, processName, state.status.name, state.allowedActions.toSet)

  def apply(
      actionName: ScenarioActionName,
      processName: ProcessName,
      statusName: StateStatus.StatusName,
      allowedActions: Set[ScenarioActionName]
  ): ProcessIllegalAction =
    ProcessIllegalAction(
      s"Action: $actionName is not allowed in scenario ($processName) state: ${statusName}, allowed actions: ${allowedActions
          .map(_.value)
          .mkString(",")}."
    )

  def archived(actionName: ScenarioActionName, processName: ProcessName): ProcessIllegalAction =
    ProcessIllegalAction(s"Forbidden action: $actionName for archived scenario: $processName.")

  def fragment(actionName: ScenarioActionName, processName: ProcessName): ProcessIllegalAction =
    ProcessIllegalAction(s"Forbidden action: $actionName for fragment: $processName.")

}
