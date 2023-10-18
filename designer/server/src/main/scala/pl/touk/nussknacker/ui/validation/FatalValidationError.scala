package pl.touk.nussknacker.ui.validation

import pl.touk.nussknacker.restmodel.validation.ValidationResults.{NodeValidationError, ValidationResult}
import pl.touk.nussknacker.ui.{BadRequestError, NuDesignerError}
import pl.touk.nussknacker.ui.validation.FatalValidationError.formatError

object FatalValidationError {

  def renderNotAllowedAsError(validationResult: ValidationResult): Either[NuDesignerError, ValidationResult] = {
    if (validationResult.renderNotAllowedErrors.isEmpty) {
      Right(validationResult)
    } else {
      Left[NuDesignerError, ValidationResult](FatalValidationError(validationResult.renderNotAllowedErrors))
    }
  }

  def saveNotAllowedAsError(validationResult: ValidationResult): ValidationResult = {
    if (validationResult.saveNotAllowedErrors.isEmpty) {
      validationResult
    } else {
      throw FatalValidationError(validationResult.saveNotAllowedErrors)
    }
  }

  private def formatError(e: NodeValidationError) = s"${e.message}:${e.description}"

}

final case class FatalValidationError(errors: List[NodeValidationError])
    extends BadRequestError(errors.map(formatError).mkString(",")) {}
