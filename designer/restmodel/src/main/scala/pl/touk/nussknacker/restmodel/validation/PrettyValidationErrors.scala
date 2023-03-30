package pl.touk.nussknacker.restmodel.validation

import org.apache.commons.lang3.StringUtils
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError._
import pl.touk.nussknacker.engine.api.context.{ParameterValidationError, ProcessCompilationError}
import pl.touk.nussknacker.engine.api.util.ReflectUtils
import pl.touk.nussknacker.engine.graph.EdgeType
import pl.touk.nussknacker.restmodel.process.ProcessingType
import pl.touk.nussknacker.restmodel.validation.ValidationResults.NodeValidationErrorType.{RenderNotAllowed, SaveAllowed}
import pl.touk.nussknacker.restmodel.validation.ValidationResults.{NodeValidationError, NodeValidationErrorType}

object PrettyValidationErrors {
  def formatErrorMessage(error: ProcessCompilationError): NodeValidationError = {
    val typ = ReflectUtils.simpleNameWithoutSuffix(error.getClass)

    def node(message: String,
             description: String,
             errorType: NodeValidationErrorType.Value = NodeValidationErrorType.SaveAllowed,
             fieldName: Option[String] = None): NodeValidationError
    = NodeValidationError(typ, message, description, fieldName, errorType)

    def handleParameterValidationError(error: ParameterValidationError): NodeValidationError =
      node(error.message, error.description, fieldName = Some(error.paramName))

    error match {
      case ExpressionParserCompilationError(message, _, fieldName, _) => node(s"Failed to parse expression: $message",
        s"There is problem with expression in field $fieldName - it could not be parsed.", fieldName = fieldName)
      case SubprocessParamClassLoadError(fieldName, refClazzName, _) =>
        node("Invalid parameter type.", s"Failed to load $refClazzName", fieldName = Some(fieldName))
      case DuplicatedNodeIds(ids) => node("Two nodes cannot have same id", s"Duplicate node ids: ${ids.mkString(", ")}", errorType = NodeValidationErrorType.RenderNotAllowed)
      case ScenarioNameValidationError(message, description) => node(message, description
        //TODO: we should pass id here, but editing scenario id is *really* quirky...
        //  , fieldName = Some("id")
      )
      case SpecificDataValidationError(field, message) => node(message, message, fieldName = Some(field))
      case EmptyProcess => node("Empty scenario", "Scenario is empty, please add some nodes")
      case InvalidRootNode(_) => node("Invalid root node", "Scenario can start only from source node")
      case InvalidTailOfBranch(_) => node("Scenario must end with a sink, processor or fragment", "Scenario must end with a sink, processor or fragment")
      case EmptyNodeId => node("Nodes cannot have empty id", "Nodes cannot have empty id", errorType = NodeValidationErrorType.RenderNotAllowed)
      case NonUniqueEdgeType(etype, nodeId) => node("Edges are not unique", s"Node $nodeId has duplicate outgoing edges of type: $etype, it cannot be saved properly", errorType = NodeValidationErrorType.SaveNotAllowed)
      case NonUniqueEdge(nodeId, target) => node("Edges are not unique", s"Node $nodeId has duplicate outgoing edges to: $target, it cannot be saved properly", errorType = NodeValidationErrorType.SaveNotAllowed)
      case LooseNode(nodeId) => node("Loose node", s"Node $nodeId is not connected to source, it cannot be saved properly", errorType = NodeValidationErrorType.SaveNotAllowed)
      case InvalidCharacters(nodeId) => node("Invalid characters", s"Node $nodeId contains invalid characters: " + "\", . and ' are not allowed in node id", RenderNotAllowed)
      case DisabledNode(nodeId) => node(s"Node $nodeId is disabled", "Deploying scenario with disabled node can have unexpected consequences", SaveAllowed)

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
      case InvalidVariableOutputName(varName, _, paramName) => node(s"Variable output name '$varName' is not a valid identifier (only letters, numbers or '_', cannot be empty)", "Please use only letters, numbers or '_', also identifier cannot be empty.", fieldName = paramName)
      case NotSupportedExpressionLanguage(languageId, _) => node(s"Language $languageId is not supported", "Currently only SPEL expressions are supported")
      case MissingPart(id) => node("MissingPart", s"Node $id has missing part")
      case UnsupportedPart(id) => node("UnsupportedPart", s"Type of node $id is unsupported right now")
      case UnknownSubprocess(id, nodeId) => node("Unknown fragment", s"Node $nodeId uses fragment $id which is missing")
      case InvalidSubprocess(id, nodeId) => node("Invalid fragment", s"Node $nodeId uses fragment $id which is invalid")
      case FatalUnknownError(message) => node("Unknown, fatal validation error", s"Fatal error: $message, please check configuration")
      case CannotCreateObjectError(message, nodeId) => node(s"Could not create $nodeId: $message", s"Could not create $nodeId: $message")

      case UnresolvedSubprocess(id) => node("Unresolved fragment", s"fragment $id encountered, this should not happen")
      case FragmentOutputNotDefined(id, _) => node(s"Output $id not defined", "Please check fragment definition")
      case UnknownFragmentOutput(id, _) => node(s"Unknown fragment output $id", "Please check fragment definition")
      case DisablingManyOutputsSubprocess(id, _) => node(s"Cannot disable fragment $id. Has many outputs", "Please check fragment definition")
      case DisablingNoOutputsSubprocess(id) => node(s"Cannot disable fragment $id. Hasn't outputs", "Please check fragment definition")
      case MissingRequiredProperty(fieldName, label, _) => missingRequiredProperty(typ, fieldName, label)
      case UnknownProperty(propertyName, _) => unknownProperty(typ, propertyName)
      case InvalidPropertyFixedValue(fieldName, label, value, values, _) => invalidPropertyFixedValue(typ, fieldName, label, value, values)
      case CustomNodeError(_, message, paramName) => NodeValidationError(typ, message, message, paramName, NodeValidationErrorType.SaveAllowed)
      case MultipleOutputsForName(name, _) => node(s"There is more than one output with '$name' name defined in the fragment, currently this is not allowed", "Please check fragment definition")
    }
  }

  def noValidatorKnown(typ: ProcessingType): NodeValidationError = {
    NodeValidationError(typ, s"No validator available for $typ", "No validator for scenario type - please check configuration", fieldName = None,
      errorType = NodeValidationErrorType.RenderNotAllowed)
  }

  private def unknownProperty(typ: String, propertyName: String): NodeValidationError =
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
