package pl.touk.esp.ui.api

import cats.data.{NonEmptyList, Xor}
import pl.touk.esp.engine.compile.ProcessCompilationError._
import pl.touk.esp.engine.compile.{ProcessCompilationError, ProcessValidator}
import pl.touk.esp.engine.util.ReflectUtils
import pl.touk.esp.ui.EspError
import pl.touk.esp.ui.api.ProcessValidation.{NodeValidationError, ValidationResult}
import pl.touk.esp.ui.process.displayedgraph.DisplayableProcess
import pl.touk.esp.ui.process.marshall.ProcessConverter
import cats.implicits._

class ProcessValidation(processValidator: ProcessValidator) {

  val uiValidationError = "UiValidation"

  import pl.touk.esp.ui.util.CollectionsEnrichments._

  def validate(displayable: DisplayableProcess): ValidationResult = {
    val canonical = ProcessConverter.fromDisplayable(displayable)
    val compilationValidationResult = processValidator.validate(canonical).leftMap(formatErrors).swap.getOrElse(ValidationResult.success)
    val uiValidationResult = uiValidation(displayable)
    compilationValidationResult.add(uiValidationResult)
  }

  private def uiValidation(displayable: DisplayableProcess) : ValidationResult = {
    validateIds(displayable).add(validateDuplicates(displayable))
  }

  private def validateIds(displayable: DisplayableProcess) : ValidationResult = {
    val invalidCharsRegexp = "[\"']".r

    ValidationResult(
      displayable.nodes.map(_.id).filter(n => invalidCharsRegexp.findFirstIn(n).isDefined)
        .map(n => n -> List(NodeValidationError(uiValidationError, "Node id contains invalid characters",
        "\" and ' are not allowed in node id", isFatal = true, fieldName = None))).toMap,
      List(),
      List()
    )
  }

  private def validateDuplicates(displayable: DisplayableProcess) : ValidationResult = {
    val duplicates = displayable.nodes.groupBy(_.id).filter(_._2.size > 1).keys
    if (duplicates.isEmpty) {
      ValidationResult.success
    } else {
      ValidationResult(Map(), List(), List(NodeValidationError(uiValidationError,
        s"Duplicate node ids: ${duplicates.mkString(", ")}", "Two nodes cannot have same id", fieldName = None, isFatal = true)))
    }

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
    def node(message: String, description: String,
             isFatal: Boolean = false,
             fieldName: Option[String] = None) = NodeValidationError(typ, message, description, fieldName, isFatal)
    error match {
      case ExpressionParseError(message, _, fieldName, _) => node(s"Failed to parse expression: $message",
        s"There is problem with expression in field $fieldName - it could not be parsed.", isFatal = false, fieldName)
      case DuplicatedNodeIds(ids) => node(s"Duplicate node ids: ${ids.mkString(", ")}", "Two nodes cannot have same id", isFatal = true)
      case EmptyProcess => node("Empty process", "Process is empty, please add some nodes")
      case InvaliRootNode(_) => node("Invalid root node", "Process can start only from source node")
      case InvalidTailOfBranch(_) => node("Invalid end of process", "Process branch can only end with sink or processor")

      case MissingParameters(params, ProcessCompilationError.ProcessNodeId) =>
        node(s"Global process parameters not filled", s"Please fill process properties ${params.mkString(", ")} by clicking 'Properties button'")
      case MissingParameters(params, _) =>
        node(s"Node parameters not filled", s"Please fill missing node parameters: : ${params.mkString(", ")}")
      //exceptions below should not really happen (unless servieces change and process becomes invalid
      case MissingCustomNodeExecutor(id, _) => node(s"Missing custom executor: $id", s"Please check the name of custom executor, $id is not available")
      case MissingService(id, _) => node(s"Missing processor/enricher: $id", s"Please check the name of processor/enricher, $id is not available")
      case MissingSinkFactory(id, _) => node(s"Missing sink: $id", s"Please check the name of sink, $id is not available")
      case MissingSourceFactory(id, _) => node(s"Missing source: $id", s"Please check the name of source, $id is not available")
      case RedundantParameters(params, _) => node(s"Redundant parameters", s"Please omit redundant parameters: ${params.mkString(", ")}")
      case WrongParameters(requiredParameters, passedParameters, _) =>
        node(s"Wrong parameters", s"Please provide ${requiredParameters.mkString(", ")} instead of ${passedParameters.mkString(", ")}")

      case NotSupportedExpressionLanguage(languageId, _) => node(s"Language $languageId is not supported", "Currently only SPEL expressions are supported")
    }
  }

}

object ProcessValidation {

  case class ValidationResult(invalidNodes: Map[String, List[NodeValidationError]],
                              processPropertiesErrors: List[NodeValidationError],
                              globalErrors: List[NodeValidationError]) {
    def add(other: ValidationResult) = ValidationResult(
      invalidNodes.combine(other.invalidNodes),
      processPropertiesErrors ++ other.processPropertiesErrors,
      globalErrors ++ other.globalErrors
    )

    def fatalAsError = if (fatalErrors.isEmpty) {
      Xor.right(this)
    } else {
      Xor.left[EspError, ValidationResult](FatalValidationError(fatalErrors.map(_.message).mkString(",")))
    }

    def fatalErrors = (invalidNodes.values.flatten ++ processPropertiesErrors ++ globalErrors).filter(_.isFatal)
  }

  case class FatalValidationError(message: String) extends EspError {
    override def getMessage = message
  }

  case class NodeValidationError(typ: String, message: String, description: String, fieldName: Option[String], isFatal: Boolean)

  object ValidationResult {
    val success = ValidationResult(Map.empty, List(), List())
  }

}