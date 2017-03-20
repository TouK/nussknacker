package pl.touk.esp.ui.validation

import pl.touk.esp.engine.compile.ProcessCompilationError
import pl.touk.esp.engine.compile.ProcessCompilationError._
import pl.touk.esp.engine.util.ReflectUtils
import pl.touk.esp.ui.validation.ValidationResults.NodeValidationError

object PrettyValidationErrors {
  def formatErrorMessage(error: ProcessCompilationError): NodeValidationError = {
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
      case OverwrittenVariable(varName, _) => node(s"Variable $varName is already defined", "You cannot overwrite variables")
      case NotSupportedExpressionLanguage(languageId, _) => node(s"Language $languageId is not supported", "Currently only SPEL expressions are supported")
      case MissingPart(id) => node("MissingPart", s"Node $id has missing part")
      case WrongProcessType() => node("Wrong process type", "Process type doesn't match category - please check configuration")
      case UnsupportedPart(id) => node("UnsupportedPart", s"Type of node $id is unsupported right now")
    }
  }

  def invalidCharacters(typ: String) = {
    NodeValidationError(typ, "Node id contains invalid characters",
      "\" and ' are not allowed in node id", isFatal = true, fieldName = None)
  }
  def duplicatedNodeIds(typ: String, duplicates: List[String]) = {
    NodeValidationError(typ: String,
      s"Duplicate node ids: ${duplicates.mkString(", ")}", "Two nodes cannot have same id", fieldName = None, isFatal = true)
  }

  def disabledNode(typ: String) = {
    NodeValidationError(typ, s"Node is disabled", "Deploying process with disabled node can have unexpected consequences", fieldName = None, isFatal = false)
  }
}
