package pl.touk.esp.ui.api

import cats.data.Validated.{Invalid, Valid}
import cats.data.{NonEmptyList, Validated}
import pl.touk.esp.engine.canonicalgraph.CanonicalProcess
import pl.touk.esp.engine.compile.{ProcessCompilationError, ProcessValidator}
import pl.touk.esp.engine.util.ReflectUtils
import pl.touk.esp.ui.api.ProcessValidation.ValidationResult

trait ProcessValidation {

  import pl.touk.esp.ui.util.CollectionsEnrichments._

  protected def processValidator: ProcessValidator

  protected def validateFilteringResults(canonical: CanonicalProcess, nodeId: String): ValidationResult = {
    validate(canonical) match {
      case Valid(_) =>
        ValidationResult(Map.empty)
      case Invalid(errors) =>
        errors.filterNode(nodeId)
    }
  }

  protected def validate(canonical: CanonicalProcess): Validated[ValidationResult, Unit] = {
    processValidator.validate(canonical).leftMap(formatErrors)
  }

  private def formatErrors(errors: NonEmptyList[ProcessCompilationError]): ValidationResult = {
    ValidationResult(
      (for {
        error <- errors.toList
        nodeId <- error.nodeIds
      } yield nodeId -> formatErrorMessage(error)).flatGroupByKey
    )
  }

  private def formatErrorMessage(error: ProcessCompilationError): String = {
    ReflectUtils.fixedClassSimpleNameWithoutParentModule(error.getClass) // TODO: better messages
  }

}

object ProcessValidation {

  case class ValidationResult(invalidNodes: Map[String, List[String]]) {
    def filterNode(nodeId: String) =
      copy(invalidNodes = invalidNodes.filterKeys(_ == nodeId))
  }

}