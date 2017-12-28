package pl.touk.nussknacker.ui.validation

import pl.touk.nussknacker.engine.compile.ProcessCompilationError
import pl.touk.nussknacker.engine.compile.ProcessCompilationError._
import pl.touk.nussknacker.engine.util.ReflectUtils
import pl.touk.nussknacker.ui.db.entity.ProcessEntity.ProcessingType
import pl.touk.nussknacker.ui.db.entity.ProcessEntity.ProcessingType.ProcessingType
import pl.touk.nussknacker.ui.process.displayedgraph.displayablenode.EdgeType
import pl.touk.nussknacker.ui.validation.ValidationResults.{NodeValidationError, NodeValidationErrorType}

object PrettyValidationErrors {
  def formatErrorMessage(error: ProcessCompilationError): NodeValidationError = {
    val typ = ReflectUtils.fixedClassSimpleNameWithoutParentModule(error.getClass)
    def node(message: String, description: String,
             errorType: NodeValidationErrorType.Value = NodeValidationErrorType.SaveAllowed,
             fieldName: Option[String] = None) = NodeValidationError(typ, message, description, fieldName, errorType)
    error match {
      case ExpressionParseError(message, _, fieldName, _) => node(s"Failed to parse expression: $message",
        s"There is problem with expression in field $fieldName - it could not be parsed.", fieldName = fieldName)
      case DuplicatedNodeIds(ids) => node(s"Duplicate node ids: ${ids.mkString(", ")}", "Two nodes cannot have same id", errorType = NodeValidationErrorType.RenderNotAllowed)
      case EmptyProcess => node("Empty process", "Process is empty, please add some nodes")
      case InvalidRootNode(_) => node("Invalid root node", "Process can start only from source node")
      case InvalidTailOfBranch(_) => node("Invalid end of process", "Process branch can only end with sink or processor")

      case MissingParameters(params, ProcessCompilationError.ProcessNodeId) =>
        node(s"Global process parameters not filled", s"Please fill process properties ${params.mkString(", ")} by clicking 'Properties button'")
      case MissingParameters(params, _) =>
        node(s"Node parameters not filled", s"Please fill missing node parameters: : ${params.mkString(", ")}")
      //exceptions below should not really happen (unless services change and process becomes invalid)
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
      case UnknownSubprocess(id, nodeId) => node("Unknown subprocess", s"Node $nodeId uses subprocess $id which is missing")
      case InvalidSubprocess(id, nodeId) => node("Invalid subprocess", s"Node $nodeId uses subprocess $id which is invalid")

      case UnresolvedSubprocess(id) => node("Unresolved subprocess", s"Subprocess $id encountered, this should not happen")
      case NoParentContext(_) => node("No parent context", "Please check subprocess definition")
      case UnknownSubprocessOutput(id, _) => node(s"Unknown subprocess output $id", "Please check subprocess definition")
    }
  }

  def noValidatorKnown(typ: ProcessingType.Value): NodeValidationError = {
    NodeValidationError(typ.toString, s"No validator available for $typ", "No validator for process type - please check configuration", fieldName = None,
      errorType = NodeValidationErrorType.RenderNotAllowed)
  }

  def invalidCharacters(typ: String): NodeValidationError = {
    NodeValidationError(typ, "Node id contains invalid characters",
      "\" and ' are not allowed in node id", fieldName = None, errorType = NodeValidationErrorType.RenderNotAllowed)
  }
  def duplicatedNodeIds(typ: String, duplicates: List[String]): NodeValidationError = {
    NodeValidationError(typ, "Two nodes cannot have same id", s"Duplicate node ids: ${duplicates.mkString(", ")}", fieldName = None,
      errorType = NodeValidationErrorType.RenderNotAllowed)
  }

  def nonuniqeEdge(typ: String, etype: EdgeType): NodeValidationError = {
    NodeValidationError(typ, "Edges are not unique",
      s"Node has duplicate outgoing edges of type: $etype, it cannot be saved properly", fieldName = None, errorType = NodeValidationErrorType.SaveNotAllowed)
  }

  def looseNode(typ: String): NodeValidationError = {
    NodeValidationError(typ, "Loose node",
      s"Node is not connected to source, it cannot be saved properly", fieldName = None, errorType = NodeValidationErrorType.SaveNotAllowed)
  }

  def tooManySources(typ: String, ids: List[String]): NodeValidationError = {
    NodeValidationError(typ, "Too many inputs",
      s"Process have multiple inputs: $ids, it cannot be saved properly", fieldName = None, errorType = NodeValidationErrorType.SaveNotAllowed)
  }

  def disabledNode(typ: String): NodeValidationError = {
    NodeValidationError(typ, s"Node is disabled", "Deploying process with disabled node can have unexpected consequences", fieldName = None, errorType = NodeValidationErrorType.SaveAllowed)
  }
}
