package pl.touk.nussknacker.restmodel.validation

import org.apache.commons.lang3.StringUtils
import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.api.context.{ParameterValidationError, ProcessCompilationError}
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError._
import pl.touk.nussknacker.engine.api.definition.{FixedExpressionValue, FixedValuesParameterEditor}
import pl.touk.nussknacker.engine.api.generics.ExpressionParseError.{
  ErrorDetails,
  IncompatibleParameterDefinitionErrorDetails
}
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.util.ReflectUtils
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.graph.node._
import pl.touk.nussknacker.restmodel.validation.ValidationResults.{NodeValidationError, NodeValidationErrorType}

object PrettyValidationErrors {

  def formatErrorMessage(error: ProcessCompilationError): NodeValidationError = {
    val typ = getErrorTypeName(error)

    def node(
        message: String,
        description: String,
        errorType: NodeValidationErrorType.Value = NodeValidationErrorType.SaveAllowed,
        paramName: Option[ParameterName] = None,
        details: Option[ErrorDetails] = None
    ): NodeValidationError = NodeValidationError(typ, message, description, paramName.map(_.value), errorType, details)

    def handleParameterValidationError(error: ParameterValidationError): NodeValidationError =
      node(error.message, error.description, paramName = Some(error.paramName))

    error match {
      case ExpressionParserCompilationError(message, _, paramName, _, details) =>
        node(
          message = message,
          description =
            s"There is problem with expression in field [${paramName.map(_.value).getOrElse("<missing>")}] - it could not be parsed.",
          paramName = paramName,
          details = details
        )
      case FragmentParamClassLoadError(paramName, refClazzName, _) =>
        node(
          message = "Invalid parameter type.",
          description = s"Failed to load $refClazzName",
          paramName = Some(qualifiedParamFieldName(paramName = paramName, subFieldName = Some(TypFieldName)))
        )
      case DuplicatedNodeIds(ids) =>
        node(
          message = "Two nodes cannot have same id",
          description = s"Duplicate node ids: ${ids.mkString(", ")}",
          errorType = NodeValidationErrorType.RenderNotAllowed
        )
      case EmptyProcess       => node("Empty scenario", "Scenario is empty, please add some nodes")
      case InvalidRootNode(_) => node("Invalid root node", "Scenario can start only from source node")
      case InvalidTailOfBranch(_) =>
        node(
          "Scenario must end with a sink, processor or fragment",
          "Scenario must end with a sink, processor or fragment"
        )
      case error: IdError =>
        mapIdErrorToNodeError(error)
      case ScenarioNameValidationError(message, description) =>
        node(
          message = message,
          description = description,
          paramName = Some(ParameterName(CanonicalProcess.NameFieldName)),
        )
      case SpecificDataValidationError(field, message) => node(message, message, paramName = Some(field))
      case NonUniqueEdgeType(etype, _, nodeName) =>
        node(
          message = "Edges are not unique",
          description = s"Node $nodeName has duplicate outgoing edges of type: $etype, it cannot be saved properly",
          errorType = NodeValidationErrorType.SaveNotAllowed
        )
      case NonUniqueEdge(_, nodeName, target) =>
        node(
          message = "Edges are not unique",
          description = s"Node $nodeName has duplicate outgoing edges to: $target, it cannot be saved properly",
          errorType = NodeValidationErrorType.SaveNotAllowed
        )
      case LooseNode(nodes) =>
        val nodeNames = nodes.map(_._2)
        val (message, description) = nodeNames.toList match {
          case nodeName :: Nil =>
            (
              "Loose node",
              s"Node $nodeName is not connected to source, it cannot be saved properly"
            )
          case _ =>
            (
              "Loose nodes",
              s"Nodes ${nodeNames.mkString(", ")} are not connected to source, it cannot be saved properly"
            )
        }
        node(
          message,
          description,
          errorType = NodeValidationErrorType.SaveNotAllowed
        )
      case StickyNoteContentTooLong(_, length, max) =>
        val message     = "Sticky note content too Long"
        val description = s"Maximum allowed length of sticky note is: $max - found: $length"
        node(
          message,
          description,
          errorType = NodeValidationErrorType.SaveNotAllowed
        )
      case StickyNotesLimitExceeded(_, count, limit) =>
        val message = "Number of sticky notes exceeded"
        val description =
          s"Too many sticky notes. Limit is set to: $limit, but found $count sticky note${if (count > 1) "s" else ""}"
        node(
          message,
          description,
          errorType = NodeValidationErrorType.SaveNotAllowed
        )
      case DisabledNode(_, nodeName) =>
        node(
          message = s"Node $nodeName is disabled",
          description = "Deploying scenario with disabled node can have unexpected consequences",
          errorType = NodeValidationErrorType.SaveAllowed
        )

      case MissingParameters(params, _) =>
        node(
          message = s"Node parameters not filled: ${params.mkString(", ")}",
          description = s"Please fill missing node parameters: : ${params.mkString(", ")}"
        )
      case DuplicatedParameters(params, _) =>
        node(
          message = s"Node parameters duplicated: ${params.mkString(", ")}",
          description = s"Please remove duplicated node parameters: : ${params.mkString(", ")}"
        )
      case pve: ParameterValidationError => handleParameterValidationError(pve)

      // exceptions below should not really happen (unless services change and process becomes invalid)
      case MissingCustomNodeExecutor(id, _) =>
        node(s"Missing custom executor: $id", s"Please check the name of custom executor, $id is not available")
      case MissingService(id, _) =>
        node(s"Missing processor/enricher: $id", s"Please check the name of processor/enricher, $id is not available")
      case MissingSinkFactory(id, _) =>
        node(s"Missing sink: $id", s"Please check the name of sink, $id is not available")
      case MissingSourceFactory(id, _) =>
        node(s"Missing source: $id", s"Please check the name of source, $id is not available")
      case WrongParameters(requiredParameters, passedParameters, _) =>
        node(
          message = s"Wrong parameters",
          description =
            s"Please provide ${requiredParameters.map(_.value).mkString(", ")} instead of ${passedParameters.map(_.value).mkString(", ")}"
        )
      case OverwrittenVariable(varName, _, paramName) =>
        node(
          message = s"Variable name '$varName' is already defined.",
          description = "You cannot overwrite variables",
          paramName = paramName
        )
      case InvalidVariableName(varName, _, paramName) =>
        node(
          message =
            s"Variable name '$varName' is not a valid identifier (only letters, numbers or '_', cannot be empty)",
          description = "Please use only letters, numbers or '_', also identifier cannot be empty.",
          paramName = paramName
        )
      case NotSupportedExpressionLanguage(languageId, _) =>
        node(s"Language $languageId is not supported", "Currently only SPEL expressions are supported")
      case MissingPart(_, nodeName)     => node("MissingPart", s"Node $nodeName has missing part")
      case UnsupportedPart(_, nodeName) => node("UnsupportedPart", s"Type of node $nodeName is unsupported right now")
      case UnknownFragment(id, _, nodeName) =>
        node("Unknown fragment", s"Node $nodeName uses fragment $id which is missing")
      case InvalidFragment(id, _, nodeName) =>
        node("Invalid fragment", s"Node $nodeName uses fragment $id which is invalid")
      case FatalUnknownError(message, maybeNodeId) =>
        node(
          s"Unknown, fatal validation error${maybeNodeId.map(nodeId => s" for node $nodeId")}",
          s"Fatal error: $message, please check configuration and logs for details"
        )
      case CannotCreateObjectError(message, _, nodeName, _) =>
        node(s"Could not create $nodeName: $message", s"Could not create $nodeName: $message")
      case UnresolvedFragment(id) => node("Unresolved fragment", s"fragment $id encountered, this should not happen")
      case FragmentOutputNotDefined(id, _) => node(s"Output $id not defined", "Please check fragment definition")
      case UnknownFragmentOutput(id, _)    => node(s"Unknown fragment output $id", "Please check fragment definition")
      case DisablingManyOutputsFragment(_) =>
        node(s"Cannot disable fragment with multiple outputs", "Please check fragment definition")
      case DisablingNoOutputsFragment(_) =>
        node(s"Cannot disable fragment with no outputs", "Please check fragment definition")
      case ScenarioLabelValidationError(label, description) =>
        node(
          message = s"Invalid scenario label: $label",
          description = description,
          paramName = Some(ParameterName(label)),
        )
      case MissingRequiredProperty(paramName, label, _) => missingRequiredProperty(typ, paramName.value, label)
      case UnknownProperty(paramName, _)                => unknownProperty(typ, paramName.value)
      case InvalidPropertyFixedValue(paramName, label, value, values, nodeId) =>
        invalidPropertyFixedValue(typ, paramName.value, label, value, values, nodeId)
      case MultiSelectUnallowedValue(value, allowedOptions, paramName, _) =>
        val message =
          s"Value: '${value.spaces2}' is not allowed. Only allowed values are: ${allowedOptions.map(_.label).mkString(", ")}"
        node(
          message = message,
          description = message,
          paramName = Some(paramName)
        )
      case MultiSelectInvalidFormat(message, paramName, _) =>
        node(
          message = message,
          description = message,
          paramName = Some(paramName)
        )
      case CustomNodeError(_, message, paramName) =>
        NodeValidationError(typ, message, message, paramName.map(_.value), NodeValidationErrorType.SaveAllowed, None)
      case EagerExpressionEvaluationError(message, _, paramName, _) =>
        node(
          message = message,
          description = message,
          paramName = Some(paramName),
        )
      case e: DuplicateFragmentOutputNames =>
        node(
          message = s"Fragment output name '${e.duplicatedVarName}' has to be unique",
          description = "Please check fragment definition"
        )
      case DuplicateFragmentInputParameter(paramName, _) =>
        node(
          message = s"Parameter name '${paramName.value}' has to be unique",
          description = "Parameter name not unique",
          paramName = Some(qualifiedParamFieldName(paramName = paramName, subFieldName = Some(ParameterNameFieldName)))
        )
      case InitialValueNotPresentInPossibleValues(paramName, _) =>
        node(
          message =
            s"The initial value provided for parameter '${paramName.value}' is not present in the parameter's possible values list",
          description = "Please check component definition",
          paramName = Some(qualifiedParamFieldName(paramName = paramName, subFieldName = Some(InitialValueFieldName)))
        )
      case UnsupportedFixedValuesType(paramName, typ, _) =>
        node(
          message = s"Fixed values list can only be be provided for type String, Integer, Long or Boolean, found: $typ",
          description = "Please check component definition",
          paramName = Some(qualifiedParamFieldName(paramName = paramName, subFieldName = Some(TypFieldName)))
        )
      case UnsupportedDictParameterEditorType(paramName, typ, _) =>
        node(
          message =
            s"Dictionary parameter editor can only be used for parameters of type String, Integer, Long or Boolean, found: $typ",
          description = "Please check component definition",
          paramName = Some(qualifiedParamFieldName(paramName = paramName, subFieldName = Some(TypFieldName)))
        )
      case EmptyFixedListForRequiredField(paramName, _) =>
        node(
          message = s"Non-empty fixed list of values have to be declared for required parameter",
          description = "Please add a value to the list of possible values",
          paramName = Some(qualifiedParamFieldName(paramName = paramName, subFieldName = Some(InputModeFieldName)))
        )
      case ExpressionParserCompilationErrorInFragmentDefinition(message, _, paramName, subFieldName, originalExpr) =>
        node(
          message = s"$message",
          description = s"There is a problem with expression: $originalExpr",
          paramName = Some(qualifiedParamFieldName(paramName = paramName, subFieldName = subFieldName))
        )
      case InvalidValidationExpression(message, _, paramName, originalExpr) =>
        node(
          s"Invalid validation expression: $message",
          description = s"There is a problem with validation expression: $originalExpr",
          paramName =
            Some(qualifiedParamFieldName(paramName = paramName, subFieldName = Some(ValidationExpressionFieldName)))
        )
      case EmptyMandatoryField(_, qualifiedFieldName) =>
        node(
          message = s"This field is mandatory and cannot be empty",
          description = s"This field is mandatory and cannot be empty",
          paramName = Some(qualifiedFieldName)
        )
      case DictNotDeclared(dictId, _, paramName) =>
        node(
          message = s"Dictionary not declared: $dictId",
          description = s"Dictionary not declared: $dictId",
          paramName = Some(paramName)
        )
      case DictIsOfInvalidType(dictId, actualType, expectedType, _, paramName) =>
        node(
          s"Dictionary '$dictId' contains values of invalid type",
          s"Values in dictionary '$dictId' are of type '${actualType.display}' and cannot be treated as expected type: '${expectedType.display}'",
          paramName = Some(paramName)
        )
      case DictEntryWithKeyNotExists(dictId, key, possibleKeys, _, paramName) =>
        node(
          s"Dictionary $dictId doesn't contain entry with key: $key",
          description = s"Dictionary $dictId possible keys: $possibleKeys",
          paramName = Some(paramName)
        )
      case DictEntryWithLabelNotExists(dictId, label, possibleLabels, _, paramName) =>
        node(
          message = s"Dictionary $dictId doesn't contain entry with label: $label",
          description = s"Dictionary $dictId possible labels: $possibleLabels",
          paramName = Some(paramName)
        )
      case DictLabelByKeyResolutionFailed(dictId, key, _, paramName) =>
        node(
          s"Failed to resolve label for key: $key in dict: $dictId",
          description = s"Dict registry doesn't support fetching of label for dictId: $dictId",
          paramName = Some(paramName)
        )
      case KeyWithLabelExpressionParsingError(keyWithLabel, message, paramName, _) =>
        node(
          message = s"Error while parsing KeyWithLabel expression: $keyWithLabel",
          description = message,
          paramName = Some(paramName)
        )
      case IncompatibleParameterDefinitionModification(paramName, language, parameterEditors, nodeId) =>
        node(
          message =
            s"There is an incompatible parameter [${paramName.value}] in component [$nodeId]. Its value cannot be used for target editors [${parameterEditors.mkString(", ")}] ",
          description =
            s"Incompatible change to the parameter's definition detected. None of editors $parameterEditors supports '$language' language",
          paramName = Some(paramName),
          details = Some(IncompatibleParameterDefinitionErrorDetails(paramName, parameterEditors, nodeId)),
        )
    }
  }

  private def unknownProperty(typ: String, propertyName: String): NodeValidationError =
    NodeValidationError(
      typ = typ,
      message = s"Unknown property $propertyName",
      description = s"Property $propertyName is not known",
      fieldName = Some(propertyName),
      errorType = NodeValidationErrorType.SaveAllowed,
      details = None
    )

  private def missingRequiredProperty(typ: String, fieldName: String, label: Option[String]) = {
    val labelText = getLabel(label)
    NodeValidationError(
      typ = typ,
      message = s"Configured property $fieldName$labelText is missing",
      description = s"Please fill missing property $fieldName$labelText",
      fieldName = Some(fieldName),
      errorType = NodeValidationErrorType.SaveAllowed,
      details = None
    )
  }

  private def invalidPropertyFixedValue(
      typ: String,
      propertyName: String,
      label: Option[String],
      value: String,
      values: List[FixedExpressionValue],
      nodeId: NodeId,
  ) = {
    val labelText = getLabel(label)
    NodeValidationError(
      typ = typ,
      message = s"Property $propertyName$labelText has invalid value",
      description = s"Expected one of ${values.map(_.expression).mkString(", ")}, got: $value.",
      fieldName = Some(propertyName),
      errorType = NodeValidationErrorType.SaveAllowed,
      details = Some(
        IncompatibleParameterDefinitionErrorDetails(
          ParameterName(propertyName),
          List(FixedValuesParameterEditor(values)),
          nodeId
        )
      ),
    )
  }

  private def getLabel(label: Option[String]) = label match {
    case Some(text) => s" ($text)"
    case None       => StringUtils.EMPTY
  }

  private def getErrorTypeName(error: ProcessCompilationError) = ReflectUtils.simpleNameWithoutSuffix(error.getClass)

  private def mapIdErrorToNodeError(error: IdError) = {
    val validatedObjectType = error match {
      case ScenarioNameError(_, _, isFragment) => if (isFragment) "Fragment" else "Scenario"
      case NodeNameValidationError(_, _, _)    => "Node"
    }
    val errorSeverity = error match {
      case ScenarioNameError(_, _, _) => NodeValidationErrorType.SaveAllowed
      case NodeNameValidationError(errorType, _, _) =>
        errorType match {
          case ProcessCompilationError.EmptyValue | IllegalCharactersId(_) => NodeValidationErrorType.RenderNotAllowed
          case _                                                           => NodeValidationErrorType.SaveAllowed
        }
    }
    val fieldName = error match {
      case ScenarioNameError(_, _, _)       => CanonicalProcess.NameFieldName
      case NodeNameValidationError(_, _, _) => pl.touk.nussknacker.engine.graph.node.IdFieldName
    }
    val message = error.errorType match {
      case ProcessCompilationError.EmptyValue       => s"$validatedObjectType name is mandatory and cannot be empty"
      case ProcessCompilationError.BlankId          => s"$validatedObjectType name cannot be blank"
      case ProcessCompilationError.LeadingSpacesId  => s"$validatedObjectType name cannot have leading spaces"
      case ProcessCompilationError.TrailingSpacesId => s"$validatedObjectType name cannot have trailing spaces"
      case ProcessCompilationError.IllegalCharactersId(illegalCharactersHumanReadable) =>
        s"$validatedObjectType name contains invalid characters. $illegalCharactersHumanReadable are not allowed"
    }
    val description = error.errorType match {
      case ProcessCompilationError.EmptyValue      => s"Empty ${validatedObjectType.toLowerCase} name"
      case ProcessCompilationError.BlankId         => s"Blank ${validatedObjectType.toLowerCase} name"
      case ProcessCompilationError.LeadingSpacesId => s"Leading spaces in ${validatedObjectType.toLowerCase} name"
      case ProcessCompilationError.TrailingSpacesId =>
        s"Trailing spaces in ${validatedObjectType.toLowerCase} name"
      case ProcessCompilationError.IllegalCharactersId(_) =>
        s"Invalid characters in ${validatedObjectType.toLowerCase} name"
    }
    NodeValidationError(
      typ = getErrorTypeName(error),
      message = message,
      description = description,
      fieldName = Some(fieldName),
      errorType = errorSeverity,
      details = None
    )
  }

}
