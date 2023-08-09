package pl.touk.nussknacker.ui.process.exception

import pl.touk.nussknacker.ui.IllegalOperationError

object DeployingInvalidScenarioError extends IllegalOperationError {
  override val message: String = "Cannot deploy invalid scenario"
}