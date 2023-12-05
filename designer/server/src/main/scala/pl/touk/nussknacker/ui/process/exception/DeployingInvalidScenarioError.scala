package pl.touk.nussknacker.ui.process.exception

import pl.touk.nussknacker.restmodel.validation.ValidationResults
import pl.touk.nussknacker.ui.IllegalOperationError

final case class DeployingInvalidScenarioError(errors: ValidationResults.ValidationErrors)
    extends IllegalOperationError("Cannot deploy invalid scenario", errors.toString)
