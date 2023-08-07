package pl.touk.nussknacker.ui.validation

import pl.touk.nussknacker.ui.Error
import pl.touk.nussknacker.restmodel.validation.ValidationResults.{NodeValidationError, ValidationResult}

object FatalValidationError {

  def renderNotAllowedAsError(validationResult: ValidationResult): Either[Error, ValidationResult] = {
    if (validationResult.renderNotAllowedErrors.isEmpty) {
      Right(validationResult)
    } else {
      Left[Error, ValidationResult](FatalValidationError(validationResult.renderNotAllowedErrors))
    }
  }

  def saveNotAllowedAsError(validationResult: ValidationResult): Either[Error, ValidationResult] = {
    if (validationResult.saveNotAllowedErrors.isEmpty) {
      Right(validationResult)
    } else {
      Left[Error, ValidationResult](FatalValidationError(validationResult.saveNotAllowedErrors))
    }
  }


}

case class FatalValidationError(errors: List[NodeValidationError]) extends Error {

  override def getMessage: String = errors.map(formatError).mkString(",")

  private def formatError(e: NodeValidationError): String = {
    s"${e.message}:${e.description}"
  }
}
