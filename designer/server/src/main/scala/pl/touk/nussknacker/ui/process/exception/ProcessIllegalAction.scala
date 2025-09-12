package pl.touk.nussknacker.ui.process.exception

import pl.touk.nussknacker.engine.api.deployment.{ScenarioActionName, StateStatus}
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.ui.IllegalOperationError
import pl.touk.nussknacker.ui.process.deployment.scenariostatus.ScenarioStatusWithAllowedActions
import pl.touk.nussknacker.ui.process.periodic.PeriodicProcessService.PeriodicScenarioStatus

final case class ProcessIllegalAction(message: String) extends IllegalOperationError(message, details = "")

object ProcessIllegalAction {

  def apply(
      actionName: ScenarioActionName,
      processName: ProcessName,
      ScenarioStatusWithAllowedActions: ScenarioStatusWithAllowedActions
  ): ProcessIllegalAction = {
    val scenarioStatusName = ScenarioStatusWithAllowedActions.scenarioStatus match {
      case status: PeriodicScenarioStatus => status.mergedStatus.name
      case other                          => other.name
    }
    ProcessIllegalAction(
      s"Action: $actionName is not allowed, because scenario $processName is in state $scenarioStatusName, allowed actions: ${ScenarioStatusWithAllowedActions.allowedActions
          .map(_.value)
          .mkString(",")}."
    )
  }

  def archived(actionName: ScenarioActionName, processName: ProcessName): ProcessIllegalAction =
    ProcessIllegalAction(s"Forbidden action: $actionName for archived scenario: $processName.")

  def fragment(actionName: ScenarioActionName, processName: ProcessName): ProcessIllegalAction =
    ProcessIllegalAction(s"Forbidden action: $actionName for fragment: $processName.")

}
