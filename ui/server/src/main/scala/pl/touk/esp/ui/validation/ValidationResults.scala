package pl.touk.esp.ui.validation

import cats.implicits._
import pl.touk.esp.ui.EspError

object ValidationResults {

  case class ValidationResult(errors: ValidationErrors, warnings: ValidationWarnings) {
    val isOk = errors == ValidationErrors.success && warnings == ValidationWarnings.success

    def add(other: ValidationResult) = ValidationResult(
      ValidationErrors(
        errors.invalidNodes.combine(other.errors.invalidNodes),
        errors.processPropertiesErrors ++ other.errors.processPropertiesErrors,
        errors.globalErrors ++ other.errors.globalErrors),
      ValidationWarnings(
        warnings.invalidNodes.combine(other.warnings.invalidNodes)
      )
    )

    def fatalErrors: List[NodeValidationError] = {
      (errors.invalidNodes.values.flatten ++ errors.processPropertiesErrors ++ errors.globalErrors).filter(_.isFatal).toList
    }

    def fatalAsError: Either[EspError, ValidationResult] = {
      if (fatalErrors.isEmpty) {
        Right(this)
      } else {
        Left[EspError, ValidationResult](FatalValidationError(fatalErrors.map(_.message).mkString(",")))
      }
    }
  }

  case class ValidationErrors(invalidNodes: Map[String, List[NodeValidationError]],
                              processPropertiesErrors: List[NodeValidationError],
                              globalErrors: List[NodeValidationError]) {
    def isEmpty = invalidNodes.isEmpty && processPropertiesErrors.isEmpty && globalErrors.isEmpty
  }
  object ValidationErrors {
    val success = ValidationErrors(Map.empty, List(), List())
  }

  case class ValidationWarnings(invalidNodes: Map[String, List[NodeValidationError]])
  object ValidationWarnings {
    val success = ValidationWarnings(Map.empty)
  }

  object ValidationResult {
    val success = ValidationResult(ValidationErrors.success, ValidationWarnings.success)

    def errors(invalidNodes: Map[String, List[NodeValidationError]],
               processPropertiesErrors: List[NodeValidationError],
               globalErrors: List[NodeValidationError]) = {
      ValidationResult(
        ValidationErrors(invalidNodes = invalidNodes, processPropertiesErrors = processPropertiesErrors,
          globalErrors = globalErrors
        ),
        ValidationWarnings.success
      )
    }

    def warnings(invalidNodes: Map[String, List[NodeValidationError]]) = {
      ValidationResult(
        ValidationErrors.success,
        ValidationWarnings(invalidNodes = invalidNodes)
      )
    }
  }

  case class NodeValidationError(typ: String, message: String, description: String, fieldName: Option[String], isFatal: Boolean)

  case class FatalValidationError(message: String) extends EspError {
    override def getMessage = message
  }
}
