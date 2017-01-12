package pl.touk.esp.ui.api

import cats.data.NonEmptyList
import pl.touk.esp.engine.canonicalgraph.CanonicalProcess
import pl.touk.esp.engine.compile.ProcessCompilationError.ExpressionParseError
import pl.touk.esp.engine.compile.{ProcessCompilationError, ProcessValidator}
import pl.touk.esp.engine.util.ReflectUtils
import pl.touk.esp.ui.api.ProcessValidation.{NodeValidationError, ValidationResult}

class ProcessValidation(processValidator: ProcessValidator) {

  import pl.touk.esp.ui.util.CollectionsEnrichments._

  def validate(canonical: CanonicalProcess): ValidationResult = {
    processValidator.validate(canonical).leftMap(formatErrors).swap.getOrElse(ValidationResult.success)
  }

  private def formatErrors(errors: NonEmptyList[ProcessCompilationError]): ValidationResult = {
    val globalErrors = errors.filter(_.nodeIds.isEmpty)
    val processPropertyErrors = errors.filter(_.nodeIds == Set(ProcessCompilationError.ProcessNodeId))

    ValidationResult(
      (for {
        error <- errors.toList.filterNot(globalErrors.contains).filterNot(processPropertyErrors.contains)
        nodeId <- error.nodeIds
      } yield nodeId -> formatErrorMessage(error)).flatGroupByKey,
      processPropertyErrors.map(formatErrorMessage),
      globalErrors.map(formatErrorMessage)
    )
  }

  private def formatErrorMessage(error: ProcessCompilationError): NodeValidationError = {
    val typ = ReflectUtils.fixedClassSimpleNameWithoutParentModule(error.getClass)
    //TODO: lepsze komunikaty o bledach?
    error match {
      case ExpressionParseError(message, _, fieldName, _) => NodeValidationError(typ, message, fieldName)
      case _ => NodeValidationError(typ, typ, None)

    }
  }

}

object ProcessValidation {

  case class ValidationResult(invalidNodes: Map[String, List[NodeValidationError]],
                              processPropertiesErrors: List[NodeValidationError],
                              globalErrors: List[NodeValidationError])

  case class NodeValidationError(typ: String, message: String, fieldName: Option[String])

  object ValidationResult {
    val success = ValidationResult(Map.empty, List(), List())
  }

}