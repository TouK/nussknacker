package pl.touk.nussknacker.engine.api.context

import cats.Applicative
import cats.data.{NonEmptyList, ValidatedNel}
import io.circe.Json
import pl.touk.nussknacker.engine.api.{NodeId, NodeName}
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.InASingleNode
import pl.touk.nussknacker.engine.api.definition.{FixedExpressionValue, MultiSelectFixedValue, ParameterEditor}
import pl.touk.nussknacker.engine.api.generics.ExpressionParseError.ErrorDetails
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.graph.expression.Expression.Language

sealed trait ProcessCompilationError {
  def nodeIds: Set[NodeId]
}

sealed trait ProcessUncanonizationError extends ProcessCompilationError

sealed trait PartSubGraphCompilationError extends ProcessCompilationError

sealed trait ParameterValidationError extends PartSubGraphCompilationError with InASingleNode {
  def message: String

  def description: String

  def paramName: ParameterName
}

object ProcessCompilationError {

  type ValidatedNelCompilationError[T] = ValidatedNel[ProcessCompilationError, T]

  val ValidatedNelApplicative: Applicative[ValidatedNelCompilationError] =
    Applicative[ValidatedNelCompilationError]

  sealed trait InASingleNode {
    self: ProcessCompilationError =>

    override def nodeIds: Set[NodeId] = Set(nodeId)

    protected def nodeId: NodeId

  }

  // Errors which can't be fixed by editing node parameters without context of entire scenario
  sealed trait ScenarioGraphLevelError { self: ProcessCompilationError => }

  // All errors which we want to be seen in process as properties errors should extend this trait
  sealed trait ScenarioPropertiesError {
    self: ProcessCompilationError =>
    override def nodeIds: Set[NodeId] = Set()
  }

  final case class UnsupportedPart(nodeId: NodeId, nodeName: NodeName)
      extends ProcessCompilationError
      with InASingleNode

  final case class MissingPart(nodeId: NodeId, nodeName: NodeName) extends ProcessCompilationError with InASingleNode

  final case class InvalidRootNode(nodeIds: Set[NodeId]) extends ProcessUncanonizationError with ScenarioGraphLevelError

  object EmptyProcess extends ProcessUncanonizationError with ScenarioGraphLevelError {
    override def nodeIds = Set()
  }

  final case class InvalidTailOfBranch(nodeIds: Set[NodeId])
      extends ProcessUncanonizationError
      with ScenarioGraphLevelError

  final case class DuplicatedNodeIds(nodeIds: Set[NodeId]) extends ProcessCompilationError with ScenarioGraphLevelError

  final case class DuplicatedNodeNames(duplicatedNames: Set[NodeName], duplicatedIds: Set[NodeId])
      extends ProcessCompilationError
      with ScenarioGraphLevelError {
    override def nodeIds: Set[NodeId] = duplicatedIds
  }

  final case class NonUniqueEdgeType(edgeType: String, nodeId: NodeId, nodeName: NodeName)
      extends ProcessCompilationError
      with InASingleNode

  final case class NonUniqueEdge(nodeId: NodeId, nodeName: NodeName, target: String)
      extends ProcessCompilationError
      with InASingleNode

  final case class LooseNode(nodes: Set[(NodeId, NodeName)])
      extends ProcessCompilationError
      with ScenarioGraphLevelError {
    override def nodeIds: Set[NodeId] = nodes.map(_._1)
  }

  final case class StickyNotesLimitExceeded(nodeId: NodeId, notesCount: Int, notesLimit: Int)
      extends ProcessCompilationError
      with InASingleNode

  final case class StickyNoteContentTooLong(nodeId: NodeId, length: Int, max: Int)
      extends ProcessCompilationError
      with InASingleNode

  final case class DisabledNode(nodeId: NodeId, nodeName: NodeName) extends ProcessCompilationError with InASingleNode

  final case class NotSupportedExpressionLanguage(languageId: Language, nodeId: NodeId)
      extends PartSubGraphCompilationError
      with InASingleNode

  object NotSupportedExpressionLanguage {
    def apply(languageId: Language)(implicit nodeId: NodeId): PartSubGraphCompilationError =
      NotSupportedExpressionLanguage(languageId, nodeId)
  }

  final case class ExpressionParserCompilationError(
      message: String,
      nodeId: NodeId,
      paramName: Option[ParameterName],
      originalExpr: String,
      details: Option[ErrorDetails]
  ) extends PartSubGraphCompilationError
      with InASingleNode

  final case class ExpressionParserCompilationErrorInFragmentDefinition(
      message: String,
      nodeId: NodeId,
      paramName: ParameterName,
      subFieldName: Option[String],
      originalExpr: String
  ) extends PartSubGraphCompilationError
      with InASingleNode

  final case class InvalidValidationExpression(
      message: String,
      nodeId: NodeId,
      paramName: ParameterName,
      originalExpr: String
  ) extends PartSubGraphCompilationError
      with InASingleNode

  object ExpressionParserCompilationError {

    def apply(message: String, paramName: Option[ParameterName], originalExpr: String, details: Option[ErrorDetails])(
        implicit nodeId: NodeId
    ): PartSubGraphCompilationError =
      ExpressionParserCompilationError(message, nodeId, paramName, originalExpr, details)

  }

  final case class FragmentParamClassLoadError(paramName: ParameterName, refClazzName: String, nodeId: NodeId)
      extends PartSubGraphCompilationError
      with InASingleNode

  final case class MissingService(serviceId: String, nodeId: NodeId)
      extends PartSubGraphCompilationError
      with InASingleNode

  object MissingService {
    def apply(serviceId: String)(implicit nodeId: NodeId): PartSubGraphCompilationError =
      MissingService(serviceId, nodeId)
  }

  final case class MissingSourceFactory(typ: String, nodeId: NodeId) extends ProcessCompilationError with InASingleNode

  object MissingSourceFactory {
    def apply(typ: String)(implicit nodeId: NodeId): ProcessCompilationError =
      MissingSourceFactory(typ, nodeId)
  }

  final case class MissingSinkFactory(typ: String, nodeId: NodeId) extends ProcessCompilationError with InASingleNode

  object MissingSinkFactory {
    def apply(typ: String)(implicit nodeId: NodeId): ProcessCompilationError =
      MissingSinkFactory(typ, nodeId)
  }

  final case class MissingCustomNodeExecutor(name: String, nodeId: NodeId)
      extends ProcessCompilationError
      with InASingleNode

  object MissingCustomNodeExecutor {
    def apply(name: String)(implicit nodeId: NodeId): ProcessCompilationError =
      MissingCustomNodeExecutor(name, nodeId)
  }

  case class MissingParameters(params: Set[ParameterName], nodeId: NodeId)
      extends PartSubGraphCompilationError
      with InASingleNode

  case class DuplicatedParameters(params: Set[ParameterName], nodeId: NodeId)
      extends PartSubGraphCompilationError
      with InASingleNode

  final case class UnresolvedFragment(nodeId: NodeId) extends PartSubGraphCompilationError with InASingleNode

  object MissingParameters {
    def apply(params: Set[ParameterName])(implicit nodeId: NodeId): PartSubGraphCompilationError =
      MissingParameters(params, nodeId)
  }

  final case class WrongParameters(
      requiredParameters: Set[ParameterName],
      passedParameters: Set[ParameterName],
      nodeId: NodeId
  ) extends PartSubGraphCompilationError
      with InASingleNode

  object WrongParameters {

    def apply(requiredParameters: Set[ParameterName], passedParameters: Set[ParameterName])(
        implicit nodeId: NodeId
    ): PartSubGraphCompilationError =
      WrongParameters(requiredParameters, passedParameters, nodeId)

  }

  final case class BlankParameter(message: String, description: String, paramName: ParameterName, nodeId: NodeId)
      extends ParameterValidationError

  final case class EmptyMandatoryParameter(
      message: String,
      description: String,
      paramName: ParameterName,
      nodeId: NodeId
  ) extends ParameterValidationError

  final case class CompileTimeEvaluableParameterNotEvaluated(
      message: String,
      description: String,
      paramName: ParameterName,
      nodeId: NodeId
  ) extends ParameterValidationError

  final case class InvalidIntegerLiteralParameter(
      message: String,
      description: String,
      paramName: ParameterName,
      nodeId: NodeId
  ) extends ParameterValidationError

  final case class MismatchParameter(message: String, description: String, paramName: ParameterName, nodeId: NodeId)
      extends ParameterValidationError

  final case class LowerThanRequiredParameter(
      message: String,
      description: String,
      paramName: ParameterName,
      nodeId: NodeId
  ) extends ParameterValidationError

  final case class GreaterThanRequiredParameter(
      message: String,
      description: String,
      paramName: ParameterName,
      nodeId: NodeId
  ) extends ParameterValidationError

  final case class JsonRequiredParameter(
      message: String,
      description: String,
      paramName: ParameterName,
      nodeId: NodeId
  ) extends ParameterValidationError

  final case class CustomParameterValidationError(
      message: String,
      description: String,
      paramName: ParameterName,
      nodeId: NodeId
  ) extends ParameterValidationError

  final case class MissingRequiredProperty(paramName: ParameterName, label: Option[String], nodeId: NodeId)
      extends PartSubGraphCompilationError
      with InASingleNode

  object MissingRequiredProperty {
    def apply(paramName: ParameterName, label: Option[String])(implicit nodeId: NodeId): PartSubGraphCompilationError =
      MissingRequiredProperty(paramName, label, nodeId)
  }

  final case class UnknownProperty(paramName: ParameterName, nodeId: NodeId)
      extends PartSubGraphCompilationError
      with InASingleNode

  object UnknownProperty {
    def apply(paramName: ParameterName)(implicit nodeId: NodeId): PartSubGraphCompilationError =
      UnknownProperty(paramName, nodeId)
  }

  final case class InvalidPropertyFixedValue(
      paramName: ParameterName,
      label: Option[String],
      value: String,
      values: List[FixedExpressionValue],
      nodeId: NodeId
  ) extends PartSubGraphCompilationError
      with InASingleNode

  object InvalidPropertyFixedValue {

    def apply(paramName: ParameterName, label: Option[String], value: String, values: List[FixedExpressionValue])(
        implicit nodeId: NodeId
    ): PartSubGraphCompilationError =
      InvalidPropertyFixedValue(paramName, label, value, values, nodeId)

  }

  final case class MultiSelectUnallowedValue(
      value: Json,
      allowedValues: List[MultiSelectFixedValue],
      paramName: ParameterName,
      nodeId: NodeId
  ) extends PartSubGraphCompilationError
      with InASingleNode

  final case class MultiSelectInvalidFormat(message: String, paramName: ParameterName, nodeId: NodeId)
      extends PartSubGraphCompilationError
      with InASingleNode

  final case class OverwrittenVariable(variableName: String, nodeId: NodeId, paramName: Option[ParameterName])
      extends PartSubGraphCompilationError
      with InASingleNode

  object OverwrittenVariable {

    def apply(variableName: String, paramName: Option[ParameterName])(
        implicit nodeId: NodeId
    ): PartSubGraphCompilationError =
      OverwrittenVariable(variableName, nodeId, paramName)

  }

  final case class InvalidVariableName(name: String, nodeId: NodeId, paramName: Option[ParameterName])
      extends PartSubGraphCompilationError
      with InASingleNode

  object InvalidVariableName {

    def apply(variableName: String, paramName: Option[ParameterName])(
        implicit nodeId: NodeId
    ): PartSubGraphCompilationError =
      InvalidVariableName(variableName, nodeId, paramName)

  }

  final case class FragmentOutputNotDefined(id: String, nodeIds: Set[NodeId]) extends ProcessCompilationError

  final case class DuplicateFragmentInputParameter(paramName: ParameterName, nodeId: NodeId)
      extends PartSubGraphCompilationError
      with InASingleNode

  final case class EmptyFixedListForRequiredField(paramName: ParameterName, nodeIds: Set[NodeId])
      extends PartSubGraphCompilationError

  final case class InitialValueNotPresentInPossibleValues(paramName: ParameterName, nodeIds: Set[NodeId])
      extends PartSubGraphCompilationError

  final case class UnsupportedFixedValuesType(paramName: ParameterName, typ: String, nodeIds: Set[NodeId])
      extends PartSubGraphCompilationError

  final case class UnsupportedDictParameterEditorType(paramName: ParameterName, typ: String, nodeIds: Set[NodeId])
      extends PartSubGraphCompilationError

  final case class IncompatibleParameterDefinitionModification(
      paramName: ParameterName,
      language: Language,
      parameterEditors: List[ParameterEditor],
      nodeId: NodeId
  ) extends PartSubGraphCompilationError
      with InASingleNode

  final case class UnknownFragmentOutput(id: String, nodeIds: Set[NodeId]) extends ProcessCompilationError

  final case class DisablingManyOutputsFragment(fragmentNodeId: NodeId)
      extends ProcessCompilationError
      with ScenarioGraphLevelError {
    override def nodeIds: Set[NodeId] = Set(fragmentNodeId)
  }

  final case class DisablingNoOutputsFragment(fragmentNodeId: NodeId)
      extends ProcessCompilationError
      with ScenarioGraphLevelError {
    override def nodeIds: Set[NodeId] = Set(fragmentNodeId)
  }

  final case class UnknownFragment(id: String, nodeId: NodeId, nodeName: NodeName)
      extends ProcessCompilationError
      with InASingleNode

  final case class InvalidFragment(id: String, nodeId: NodeId, nodeName: NodeName)
      extends ProcessCompilationError
      with InASingleNode

  sealed trait DuplicateFragmentOutputNames extends ProcessCompilationError {
    val duplicatedVarName: String
  }

  final case class DuplicateFragmentOutputNamesInScenario(duplicatedVarName: String, nodeId: NodeId)
      extends DuplicateFragmentOutputNames
      with InASingleNode

  final case class DuplicateFragmentOutputNamesInFragment(duplicatedVarName: String, nodeIds: Set[NodeId])
      extends DuplicateFragmentOutputNames
      with ScenarioGraphLevelError

  final case class EmptyMandatoryField(nodeId: NodeId, qualifiedFieldName: ParameterName)
      extends PartSubGraphCompilationError
      with InASingleNode

  final case class DictNotDeclared(dictId: String, nodeId: NodeId, paramName: ParameterName)
      extends PartSubGraphCompilationError
      with InASingleNode

  final case class DictIsOfInvalidType(
      dictId: String,
      actualType: TypingResult,
      expectedType: TypingResult,
      nodeId: NodeId,
      paramName: ParameterName
  ) extends PartSubGraphCompilationError
      with InASingleNode

  final case class DictEntryWithLabelNotExists(
      dictId: String,
      label: String,
      possibleLabels: Option[List[String]],
      nodeId: NodeId,
      paramName: ParameterName
  ) extends PartSubGraphCompilationError
      with InASingleNode

  final case class DictEntryWithKeyNotExists(
      dictId: String,
      key: String,
      possibleKeys: Option[List[String]],
      nodeId: NodeId,
      paramName: ParameterName
  ) extends PartSubGraphCompilationError
      with InASingleNode

  final case class DictLabelByKeyResolutionFailed(
      dictId: String,
      key: String,
      nodeId: NodeId,
      paramName: ParameterName
  ) extends PartSubGraphCompilationError
      with InASingleNode

  final case class KeyWithLabelExpressionParsingError(
      keyWithLabel: String,
      message: String,
      paramName: ParameterName,
      nodeId: NodeId
  ) extends PartSubGraphCompilationError
      with InASingleNode

  final case class CustomNodeError(nodeId: NodeId, message: String, paramName: Option[ParameterName])
      extends ProcessCompilationError
      with InASingleNode

  object CustomNodeError {
    def apply(message: String, paramName: Option[ParameterName])(implicit nodeId: NodeId): CustomNodeError =
      CustomNodeError(nodeId, message, paramName)
  }

  final case class FatalUnknownError(message: String, nodeId: Option[NodeId]) extends ProcessCompilationError {
    override def nodeIds: Set[NodeId] = nodeId.toSet
  }

  final case class CannotCreateObjectError(
      message: String,
      nodeId: NodeId,
      nodeName: NodeName,
      cause: Option[Throwable]
  ) extends ProcessCompilationError
      with InASingleNode

  final case class EagerExpressionEvaluationError(
      message: String,
      nodeId: NodeId,
      paramName: ParameterName,
      cause: Throwable
  ) extends ProcessCompilationError
      with InASingleNode

  object CannotCreateObjectError {

    def apply(message: String, nodeId: NodeId, nodeName: NodeName): CannotCreateObjectError =
      new CannotCreateObjectError(message, nodeId, nodeName, cause = None)

    def apply(cause: Throwable, nodeId: NodeId, nodeName: NodeName): CannotCreateObjectError =
      new CannotCreateObjectError(cause.getMessage, nodeId, nodeName, cause = Some(cause))
  }

  final case class ScenarioNameValidationError(message: String, description: String)
      extends ProcessCompilationError
      with ScenarioPropertiesError

  final case class ScenarioLabelValidationError(label: String, description: String)
      extends ProcessCompilationError
      with ScenarioPropertiesError

  final case class SpecificDataValidationError(paramName: ParameterName, message: String)
      extends ProcessCompilationError
      with ScenarioPropertiesError

  sealed trait IdError extends ProcessCompilationError {
    val errorType: IdErrorType
    val id: String
  }

  final case class ScenarioNameError(errorType: IdErrorType, name: ProcessName, isFragment: Boolean)
      extends IdError
      with ScenarioPropertiesError {
    override val id: String = name.value
  }

  final case class NodeNameValidationError(errorType: IdErrorType, nodeName: NodeName, override val nodeId: NodeId)
      extends IdError
      with InASingleNode {
    override val id: String = nodeId.value
  }

  final case class NodeIdValidationError(errorType: IdErrorType, override val nodeId: NodeId)
      extends IdError
      with InASingleNode {
    override val id: String = nodeId.value
  }

  sealed trait IdErrorType
  object EmptyValue                                                           extends IdErrorType
  object BlankId                                                              extends IdErrorType
  object LeadingSpacesId                                                      extends IdErrorType
  object TrailingSpacesId                                                     extends IdErrorType
  final case class IllegalCharactersId(illegalCharacterHumanReadable: String) extends IdErrorType

}

final case class ScenarioCompilationErrors(errors: List[ProcessCompilationError])
    extends Exception(errors.mkString("Compilation errors: ", ", ", ""))
