package pl.touk.nussknacker.ui.validation

import pl.touk.nussknacker.ui.ResponseError
import pl.touk.nussknacker.restmodel.validation.ValidationResults.{NodeValidationError, ValidationResult}

object FatalValidationError {

  def renderNotAllowedAsError(validationResult: ValidationResult): Either[ResponseError, ValidationResult] = {
    if (validationResult.renderNotAllowedErrors.isEmpty) {
      Right(validationResult)
    } else {
      Left[ResponseError, ValidationResult](FatalValidationError(validationResult.renderNotAllowedErrors))
    }
  }

  def saveNotAllowedAsError(validationResult: ValidationResult): Either[ResponseError, ValidationResult] = {
    if (validationResult.saveNotAllowedErrors.isEmpty) {
      Right(validationResult)
    } else {
      Left[ResponseError, ValidationResult](FatalValidationError(validationResult.saveNotAllowedErrors))
    }
  }


}

case class FatalValidationError(errors: List[NodeValidationError]) extends ResponseError {
  override val message: String = errors.map(formatError).mkString(",")

  private def formatError(e: NodeValidationError): String = {
    s"${e.message}:${e.description}"
  }
}
