package pl.touk.nussknacker.ui.validation

import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.ScenarioNameValidationError
import pl.touk.nussknacker.restmodel.displayedgraph.DisplayableProcess
import pl.touk.nussknacker.restmodel.validation.PrettyValidationErrors
import pl.touk.nussknacker.restmodel.validation.ValidationResults.ValidationResult

object ScenarioIdValidator {

  def validate(displayable: DisplayableProcess): ValidationResult = {
    if (displayable.metaData.id.nonEmpty) {
      ValidationResult.success
    } else {
      val processType  = if (displayable.metaData.isFragment) "Fragment" else "Scenario"
      val errorMessage = s"$processType name is mandatory and cannot be empty"
      ValidationResult.errors(
        Map.empty,
        List(
          PrettyValidationErrors.formatErrorMessage(
            ScenarioNameValidationError(
              errorMessage,
              errorMessage
            )
          )
        ),
        Nil
      )
    }
  }

}
