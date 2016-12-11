package pl.touk.esp.ui.api

import cats.data.NonEmptyList
import pl.touk.esp.engine.canonicalgraph.CanonicalProcess
import pl.touk.esp.engine.compile.ProcessCompilationError.ExpressionParseError
import pl.touk.esp.engine.compile.{ProcessCompilationError, ProcessValidator}
import pl.touk.esp.engine.util.ReflectUtils
import pl.touk.esp.ui.api.ProcessValidation.{NodeValidationError, ValidationResult}

class ProcessValidation(processValidator: ProcessValidator) {

  import pl.touk.esp.ui.util.CollectionsEnrichments._

  def validateFilteringResults(canonical: CanonicalProcess, nodeId: String): ValidationResult = {
    validate(canonical).filterNode(nodeId)
  }

  def validate(canonical: CanonicalProcess): ValidationResult = {
    processValidator.validate(canonical).leftMap(formatErrors).swap.getOrElse(ValidationResult.success)
  }

  private def formatErrors(errors: NonEmptyList[ProcessCompilationError]): ValidationResult = {
    ValidationResult(
      (for {
        error <- errors.toList
        nodeId <- error.nodeIds
      } yield nodeId -> formatErrorMessage(error)).flatGroupByKey
    )
  }

  private def formatErrorMessage(error: ProcessCompilationError): NodeValidationError = {
    val typ = ReflectUtils.fixedClassSimpleNameWithoutParentModule(error.getClass)
    error match {
      case ExpressionParseError(message, _, fieldName, _) => NodeValidationError(typ, message, fieldName)


      case _ => NodeValidationError(typ, typ, None)

    }
  }

}

object ProcessValidation {

  case class ValidationResult(invalidNodes: Map[String, List[NodeValidationError]]) {
    def filterNode(nodeId: String) =
      copy(invalidNodes = invalidNodes.filterKeys(_ == nodeId))
  }

  case class NodeValidationError(typ: String, message: String, fieldName: Option[String])

  object ValidationResult {
    val success = ValidationResult(Map.empty)
  }

}