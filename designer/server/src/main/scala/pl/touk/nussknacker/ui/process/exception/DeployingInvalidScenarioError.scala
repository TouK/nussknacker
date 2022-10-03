package pl.touk.nussknacker.ui.process.exception

import pl.touk.nussknacker.ui.IllegalOperationError

object DeployingInvalidScenarioError extends IllegalOperationError {
  override def getMessage: String = "Cannot deploy invalid scenario"
}
