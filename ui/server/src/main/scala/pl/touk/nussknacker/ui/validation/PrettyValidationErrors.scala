package pl.touk.nussknacker.ui.validation

import org.apache.commons.lang3.StringUtils
import pl.touk.nussknacker.engine.ProcessingTypeData
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError._
import pl.touk.nussknacker.engine.api.context.{ParameterValidationError, ProcessCompilationError}
import pl.touk.nussknacker.engine.api.util.ReflectUtils
import pl.touk.nussknacker.engine.compile.NodeTypingInfo
import pl.touk.nussknacker.restmodel.displayedgraph.displayablenode.EdgeType
import pl.touk.nussknacker.restmodel.validation.ValidationResults.{NodeValidationError, NodeValidationErrorType}

object PrettyValidationErrors {
  def formatErrorMessage(error: ProcessCompilationError): NodeValidationError = {
    val typ = ReflectUtils.fixedClassSimpleNameWithoutParentModule(error.getClass)

    def node(message: String,
             description: String,
             errorType: NodeValidationErrorType.Value = NodeValidationErrorType.SaveAllowed,
             fieldName: Option[String] = None): NodeValidationError
      = NodeValidationError(typ, message, description, fieldName, errorType)

    def handleParameterValidationError(error: ParameterValidationError): NodeValidationError =
      node(error.message, error.description, fieldName = Some(error.paramName))

    error match {
      case ExpressionParseError(message, _, fieldName, _) => node(s"Failed to parse expression: $message",
        s"There is problem with expression in field $fieldName - it could not be parsed.", fieldName = fieldName)
      case SubprocessParamClassLoadError(fieldName, refClazzName, nodeId) =>
        node("Invalid parameter type.", s"Failed to load $refClazzName", fieldName = Some(fieldName))
      case DuplicatedNodeIds(ids) => node(s"Duplicate node ids: ${ids.mkString(", ")}", "Two nodes cannot have same id", errorType = NodeValidationErrorType.RenderNotAllowed)
      case EmptyProcess => node("Empty scenario", "Scenario is empty, please add some nodes")
      case InvalidRootNode(_) => node("Invalid root node", "Scenario can start only from source node")
      case InvalidTailOfBranch(_) => node("Invalid end of scenario", "Scenario branch can only end with sink, processor or ending custom transformer")

      case MissingParameters(params, NodeTypingInfo.ExceptionHandlerNodeId) =>
        node(s"Global scenario parameters not filled", s"Please fill scenario properties ${params.mkString(", ")} by clicking 'Properties button'")
      case MissingParameters(params, _) =>
        node(s"Node parameters not filled: ${params.mkString(", ")}", s"Please fill missing node parameters: : ${params.mkString(", ")}")

      case pve: ParameterValidationError => handleParameterValidationError(pve)

      //exceptions below should not really happen (unless services change and process becomes invalid)
      case MissingCustomNodeExecutor(id, _) => node(s"Missing custom executor: $id", s"Please check the name of custom executor, $id is not available")
      case MissingService(id, _) => node(s"Missing processor/enricher: $id", s"Please check the name of processor/enricher, $id is not available")
      case MissingSinkFactory(id, _) => node(s"Missing sink: $id", s"Please check the name of sink, $id is not available")
      case MissingSourceFactory(id, _) => node(s"Missing source: $id", s"Please check the name of source, $id is not available")
      case RedundantParameters(params, _) => node(s"Redundant parameters", s"Please omit redundant parameters: ${params.mkString(", ")}")
      case WrongParameters(requiredParameters, passedParameters, _) =>
        node(s"Wrong parameters", s"Please provide ${requiredParameters.mkString(", ")} instead of ${passedParameters.mkString(", ")}")
      case OverwrittenVariable(varName, _, paramName) => node(s"Variable output name '$varName' is already defined.", "You cannot overwrite variables", fieldName = paramName)
      case InvalidVariableOutputName(varName, _, paramName) => node(s"Variable output name '$varName' contains unsupported chars.", "Please use only letters, numbers or '_'.", fieldName = paramName)
      case NotSupportedExpressionLanguage(languageId, _) => node(s"Language $languageId is not supported", "Currently only SPEL expressions are supported")
      case MissingPart(id) => node("MissingPart", s"Node $id has missing part")
      case WrongProcessType() => node("Wrong scenario type", "Scenario type doesn't match category - please check configuration")
      case UnsupportedPart(id) => node("UnsupportedPart", s"Type of node $id is unsupported right now")
      case UnknownSubprocess(id, nodeId) => node("Unknown fragment", s"Node $nodeId uses fragment $id which is missing")
      case InvalidSubprocess(id, nodeId) => node("Invalid fragment", s"Node $nodeId uses fragment $id which is invalid")
      case FatalUnknownError(message) => node("Unkown, fatal validation error", s"Fatal error: $message, please check configuration")
      case CannotCreateObjectError(message, nodeId) => node(s"Could not create $nodeId: $message", s"Could not create $nodeId: $message")

      case UnresolvedSubprocess(id) => node("Unresolved fragment", s"fragment $id encountered, this should not happen")
      case NoParentContext(_) => node("No parent context", "Please check fragment definition")
      case UnknownSubprocessOutput(id, _) => node(s"Unknown fragment output $id", "Please check fragment definition")
      case DisablingManyOutputsSubprocess(id, _) => node(s"Cannot disable fragment $id. Has many outputs", "Please check fragment definition")
      case DisablingNoOutputsSubprocess(id) => node(s"Cannot disable fragment $id. Hasn't outputs", "Please check fragment definition")
      case MissingRequiredProperty(fieldName, label, _) => missingRequiredProperty(typ, fieldName, label)
      case UnknownProperty(propertyName, _) => unknownProperty(typ, propertyName)
      case InvalidPropertyFixedValue(fieldName, label, value, values, _) => invalidPropertyFixedValue(typ, fieldName, label, value, values)
      case CustomNodeError(_, message, paramName) => NodeValidationError(typ, message, message, paramName, NodeValidationErrorType.SaveAllowed)
    }
  }

  def noValidatorKnown(typ: ProcessingTypeData.ProcessingType): NodeValidationError = {
    NodeValidationError(typ, s"No validator available for $typ", "No validator for scenario type - please check configuration", fieldName = None,
      errorType = NodeValidationErrorType.RenderNotAllowed)
  }

  def invalidCharacters(typ: String): NodeValidationError = {
    NodeValidationError(typ, "Node id contains invalid characters",
      "\", . and ' are not allowed in node id", fieldName = None, errorType = NodeValidationErrorType.RenderNotAllowed)
  }

  def duplicatedNodeIds(typ: String, duplicates: List[String]): NodeValidationError = {
    NodeValidationError(typ, "Two nodes cannot have same id", s"Duplicate node ids: ${duplicates.mkString(", ")}", fieldName = None,
      errorType = NodeValidationErrorType.RenderNotAllowed)
  }

  def nonuniqeEdgeType(typ: String, etype: EdgeType): NodeValidationError = {
    NodeValidationError(typ, "Edges are not unique",
      s"Node has duplicate outgoing edges of type: $etype, it cannot be saved properly", fieldName = None, errorType = NodeValidationErrorType.SaveNotAllowed)
  }

  def nonuniqeEdge(typ: String, target: String): NodeValidationError = {
    NodeValidationError(typ, "Edges are not unique",
      s"Node has duplicate outgoing edges to: $target, it cannot be saved properly", fieldName = None, errorType = NodeValidationErrorType.SaveNotAllowed)
  }

  def looseNode(typ: String): NodeValidationError = {
    NodeValidationError(typ, "Loose node",
      s"Node is not connected to source, it cannot be saved properly", fieldName = None, errorType = NodeValidationErrorType.SaveNotAllowed)
  }

  def disabledNode(typ: String): NodeValidationError = {
    NodeValidationError(typ, s"Node is disabled", "Deploying scenario with disabled node can have unexpected consequences", fieldName = None, errorType = NodeValidationErrorType.SaveAllowed)
  }

  def unknownProperty(typ: String, propertyName: String): NodeValidationError =
    NodeValidationError(
      typ,
      s"Unknown property $propertyName",
      s"Property $propertyName is not known",
      Some(propertyName),
      NodeValidationErrorType.SaveAllowed
    )

  private def missingRequiredProperty(typ: String, fieldName: String, label: Option[String]) = {
    val labelText = getLabel(label)
    NodeValidationError(
      typ,
      s"Configured property $fieldName$labelText is missing",
      s"Please fill missing property $fieldName$labelText",
      Some(fieldName),
      NodeValidationErrorType.SaveAllowed
    )
  }

  private def invalidPropertyFixedValue(typ: String, propertyName: String, label: Option[String], value: String, values: List[String]) = {
    val labelText = getLabel(label)
    NodeValidationError(
      typ,
      s"Property $propertyName$labelText has invalid value",
      s"Expected one of ${values.mkString(", ")}, got: '$value'.",
      Some(propertyName),
      NodeValidationErrorType.SaveAllowed
    )
  }

  private def getLabel(label: Option[String]) = label match {
    case Some(text) => s" ($text)"
    case None => StringUtils.EMPTY
  }
}
