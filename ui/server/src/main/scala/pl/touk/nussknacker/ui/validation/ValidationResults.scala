package pl.touk.nussknacker.ui.validation

import cats.implicits._
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.compile.ValidationContext
import pl.touk.nussknacker.ui.EspError

object ValidationResults {

  case class ValidationResult(errors: ValidationErrors, warnings: ValidationWarnings, variableTypes: Map[String, Map[String, TypingResult]]) {
    val isOk = errors == ValidationErrors.success && warnings == ValidationWarnings.success
    val saveAllowed = allErrors.forall(_.errorType == NodeValidationErrorType.SaveAllowed)

    def add(other: ValidationResult) = ValidationResult(
      ValidationErrors(
        errors.invalidNodes.combine(other.errors.invalidNodes),
        errors.processPropertiesErrors ++ other.errors.processPropertiesErrors,
        errors.globalErrors ++ other.errors.globalErrors),
      ValidationWarnings(
        warnings.invalidNodes.combine(other.warnings.invalidNodes)
      ),
      variableTypes ++ other.variableTypes
    )

    def renderNotAllowedAsError: Either[EspError, ValidationResult] = {
      if (renderNotAllowedErrors.isEmpty) {
        Right(this)
      } else {
        Left[EspError, ValidationResult](FatalValidationError(renderNotAllowedErrors.map(formatError).mkString(",")))
      }
    }

    def saveNotAllowedAsError: Either[EspError, ValidationResult] = {
      if (saveNotAllowedErrors.isEmpty) {
        Right(this)
      } else {
        Left[EspError, ValidationResult](FatalValidationError(saveNotAllowedErrors.map(formatError).mkString(",")))
      }
    }

    def withTypes(variableTypes: Map[String, Map[String, TypingResult]]): ValidationResult
      = copy(variableTypes = variableTypes)

    private def renderNotAllowedErrors: List[NodeValidationError] = {
      allErrors.filter(_.errorType == NodeValidationErrorType.RenderNotAllowed)
    }

    private def saveNotAllowedErrors: List[NodeValidationError] = {
      allErrors.filter(_.errorType == NodeValidationErrorType.SaveNotAllowed)
    }

    private def allErrors: List[NodeValidationError] = {
      (errors.invalidNodes.values.flatten ++ errors.processPropertiesErrors ++ errors.globalErrors).toList
    }

    private def formatError(e: NodeValidationError): String = {
      s"${e.message}:${e.description}"
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
    val success = ValidationResult(ValidationErrors.success, ValidationWarnings.success, Map())

    def errors(invalidNodes: Map[String, List[NodeValidationError]],
               processPropertiesErrors: List[NodeValidationError],
               globalErrors: List[NodeValidationError],
               variableTypes: Map[String, Map[String, TypingResult]] = Map.empty): ValidationResult = {
      ValidationResult(
        ValidationErrors(invalidNodes = invalidNodes, processPropertiesErrors = processPropertiesErrors,
          globalErrors = globalErrors
        ),
        ValidationWarnings.success,
        variableTypes
      )
    }

    def warnings(invalidNodes: Map[String, List[NodeValidationError]]): ValidationResult = {
      ValidationResult(
        ValidationErrors.success,
        ValidationWarnings(invalidNodes = invalidNodes), Map()
      )
    }
  }

  case class NodeValidationError(typ: String,
                                 message: String,
                                 description: String,
                                 fieldName: Option[String],
                                 errorType: NodeValidationErrorType.Value
                                )

  object NodeValidationErrorType extends Enumeration {
    type NodeValidationErrorType = Value
    val RenderNotAllowed, SaveNotAllowed, SaveAllowed = Value
  }

  case class FatalValidationError(message: String) extends EspError {
    override def getMessage = message
  }
}
