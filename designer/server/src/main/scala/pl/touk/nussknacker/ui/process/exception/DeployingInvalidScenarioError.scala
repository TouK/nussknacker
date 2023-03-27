package pl.touk.nussknacker.ui.process.exception

import pl.touk.nussknacker.ui.IllegalOperationError

object DeployingInvalidScenarioError extends Exception("Cannot deploy invalid scenario") with IllegalOperationError