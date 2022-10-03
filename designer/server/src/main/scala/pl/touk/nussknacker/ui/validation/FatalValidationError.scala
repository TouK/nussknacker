package pl.touk.nussknacker.ui.validation

import pl.touk.nussknacker.ui.EspError
import pl.touk.nussknacker.restmodel.validation.ValidationResults.{NodeValidationError, ValidationResult}

object FatalValidationError {

  def renderNotAllowedAsError(validationResult: ValidationResult): Either[EspError, ValidationResult] = {
    if (validationResult.renderNotAllowedErrors.isEmpty) {
      Right(validationResult)
    } else {
      Left[EspError, ValidationResult](FatalValidationError(validationResult.renderNotAllowedErrors))
    }
  }

  def saveNotAllowedAsError(validationResult: ValidationResult): Either[EspError, ValidationResult] = {
    if (validationResult.saveNotAllowedErrors.isEmpty) {
      Right(validationResult)
    } else {
      Left[EspError, ValidationResult](FatalValidationError(validationResult.saveNotAllowedErrors))
    }
  }


}

case class FatalValidationError(errors: List[NodeValidationError]) extends EspError {

  override def getMessage: String = errors.map(formatError).mkString(",")

  private def formatError(e: NodeValidationError): String = {
    s"${e.message}:${e.description}"
  }
}
