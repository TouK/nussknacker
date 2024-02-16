package pl.touk.nussknacker.engine.api.context

import cats.Applicative
import cats.data.ValidatedNel
import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.InASingleNode
import pl.touk.nussknacker.engine.api.process.ProcessName

sealed trait ProcessCompilationError {
  def nodeIds: Set[String]
}

sealed trait ProcessUncanonizationError extends ProcessCompilationError

sealed trait PartSubGraphCompilationError extends ProcessCompilationError

sealed trait ParameterValidationError extends PartSubGraphCompilationError with InASingleNode {
  def message: String

  def description: String

  def paramName: String
}

object ProcessCompilationError {

  type ValidatedNelCompilationError[T] = ValidatedNel[ProcessCompilationError, T]

  val ValidatedNelApplicative: Applicative[ValidatedNelCompilationError] =
    Applicative[ValidatedNelCompilationError]

  sealed trait InASingleNode {
    self: ProcessCompilationError =>

    override def nodeIds: Set[String] = Set(nodeId)

    protected def nodeId: String

  }

  // Errors which can't be fixed by editing node parameters without context of entire scenario
  sealed trait ScenarioGraphLevelError { self: ProcessCompilationError => }

  // All errors which we want to be seen in process as properties errors should extend this trait
  sealed trait ScenarioPropertiesError {
    self: ProcessCompilationError =>
    override def nodeIds: Set[String] = Set()
  }

  final case class UnsupportedPart(nodeId: String) extends ProcessCompilationError with InASingleNode

  final case class MissingPart(nodeId: String) extends ProcessCompilationError with InASingleNode

  final case class InvalidRootNode(nodeIds: Set[String]) extends ProcessUncanonizationError with ScenarioGraphLevelError

  object EmptyProcess extends ProcessUncanonizationError with ScenarioGraphLevelError {
    override def nodeIds = Set()
  }

  final case class InvalidTailOfBranch(nodeIds: Set[String])
      extends ProcessUncanonizationError
      with ScenarioGraphLevelError

  final case class DuplicatedNodeIds(nodeIds: Set[String]) extends ProcessCompilationError with ScenarioGraphLevelError

  final case class NonUniqueEdgeType(edgeType: String, nodeId: String)
      extends ProcessCompilationError
      with InASingleNode

  final case class NonUniqueEdge(nodeId: String, target: String) extends ProcessCompilationError with InASingleNode

  final case class LooseNode(nodeIds: Set[String]) extends ProcessCompilationError with ScenarioGraphLevelError

  final case class DisabledNode(nodeId: String) extends ProcessCompilationError with InASingleNode

  final case class NotSupportedExpressionLanguage(languageId: String, nodeId: String)
      extends PartSubGraphCompilationError
      with InASingleNode

  object NotSupportedExpressionLanguage {
    def apply(languageId: String)(implicit nodeId: NodeId): PartSubGraphCompilationError =
      NotSupportedExpressionLanguage(languageId, nodeId.id)
  }

  final case class ExpressionParserCompilationError(
      message: String,
      nodeId: String,
      fieldName: Option[String],
      originalExpr: String
  ) extends PartSubGraphCompilationError
      with InASingleNode

  final case class ExpressionParserCompilationErrorInFragmentDefinition(
      message: String,
      nodeId: String,
      paramName: String,
      subFieldName: Option[String],
      originalExpr: String
  ) extends PartSubGraphCompilationError
      with InASingleNode

  final case class InvalidValidationExpression(
      message: String,
      nodeId: String,
      paramName: String,
      originalExpr: String
  ) extends PartSubGraphCompilationError
      with InASingleNode

  object ExpressionParserCompilationError {

    def apply(message: String, fieldName: Option[String], originalExpr: String)(
        implicit nodeId: NodeId
    ): PartSubGraphCompilationError =
      ExpressionParserCompilationError(message, nodeId.id, fieldName, originalExpr)

  }

  final case class FragmentParamClassLoadError(fieldName: String, refClazzName: String, nodeId: String)
      extends PartSubGraphCompilationError
      with InASingleNode

  final case class MissingService(serviceId: String, nodeId: String)
      extends PartSubGraphCompilationError
      with InASingleNode

  object MissingService {
    def apply(serviceId: String)(implicit nodeId: NodeId): PartSubGraphCompilationError =
      MissingService(serviceId, nodeId.id)
  }

  final case class MissingSourceFactory(typ: String, nodeId: String) extends ProcessCompilationError with InASingleNode

  object MissingSourceFactory {
    def apply(typ: String)(implicit nodeId: NodeId): ProcessCompilationError =
      MissingSourceFactory(typ, nodeId.id)
  }

  final case class MissingSinkFactory(typ: String, nodeId: String) extends ProcessCompilationError with InASingleNode

  object MissingSinkFactory {
    def apply(typ: String)(implicit nodeId: NodeId): ProcessCompilationError =
      MissingSinkFactory(typ, nodeId.id)
  }

  final case class MissingCustomNodeExecutor(name: String, nodeId: String)
      extends ProcessCompilationError
      with InASingleNode

  object MissingCustomNodeExecutor {
    def apply(name: String)(implicit nodeId: NodeId): ProcessCompilationError =
      MissingCustomNodeExecutor(name, nodeId.id)
  }

  case class MissingParameters(params: Set[String], nodeId: String)
      extends PartSubGraphCompilationError
      with InASingleNode

  final case class UnresolvedFragment(nodeId: String) extends PartSubGraphCompilationError with InASingleNode

  object MissingParameters {
    def apply(params: Set[String])(implicit nodeId: NodeId): PartSubGraphCompilationError =
      MissingParameters(params, nodeId.id)
  }

  final case class RedundantParameters(params: Set[String], nodeId: String)
      extends PartSubGraphCompilationError
      with InASingleNode

  object RedundantParameters {
    def apply(params: Set[String])(implicit nodeId: NodeId): PartSubGraphCompilationError =
      RedundantParameters(params, nodeId.id)
  }

  final case class WrongParameters(requiredParameters: Set[String], passedParameters: Set[String], nodeId: String)
      extends PartSubGraphCompilationError
      with InASingleNode

  object WrongParameters {

    def apply(requiredParameters: Set[String], passedParameters: Set[String])(
        implicit nodeId: NodeId
    ): PartSubGraphCompilationError =
      WrongParameters(requiredParameters, passedParameters, nodeId.id)

  }

  final case class BlankParameter(message: String, description: String, paramName: String, nodeId: String)
      extends ParameterValidationError

  final case class EmptyMandatoryParameter(message: String, description: String, paramName: String, nodeId: String)
      extends ParameterValidationError

  final case class InvalidIntegerLiteralParameter(
      message: String,
      description: String,
      paramName: String,
      nodeId: String
  ) extends ParameterValidationError

  final case class MismatchParameter(message: String, description: String, paramName: String, nodeId: String)
      extends ParameterValidationError

  final case class LowerThanRequiredParameter(message: String, description: String, paramName: String, nodeId: String)
      extends ParameterValidationError

  final case class GreaterThanRequiredParameter(message: String, description: String, paramName: String, nodeId: String)
      extends ParameterValidationError

  final case class JsonRequiredParameter(message: String, description: String, paramName: String, nodeId: String)
      extends ParameterValidationError

  final case class CustomParameterValidationError(
      message: String,
      description: String,
      paramName: String,
      nodeId: String
  ) extends ParameterValidationError

  final case class MissingRequiredProperty(paramName: String, label: Option[String], nodeId: String)
      extends PartSubGraphCompilationError
      with InASingleNode

  object MissingRequiredProperty {
    def apply(paramName: String, label: Option[String])(implicit nodeId: NodeId): PartSubGraphCompilationError =
      MissingRequiredProperty(paramName, label, nodeId.id)
  }

  final case class UnknownProperty(paramName: String, nodeId: String)
      extends PartSubGraphCompilationError
      with InASingleNode

  object UnknownProperty {
    def apply(paramName: String)(implicit nodeId: NodeId): PartSubGraphCompilationError =
      UnknownProperty(paramName, nodeId.id)
  }

  final case class InvalidPropertyFixedValue(
      paramName: String,
      label: Option[String],
      value: String,
      values: List[String],
      nodeId: String
  ) extends PartSubGraphCompilationError
      with InASingleNode

  object InvalidPropertyFixedValue {

    def apply(paramName: String, label: Option[String], value: String, values: List[String])(
        implicit nodeId: NodeId
    ): PartSubGraphCompilationError =
      InvalidPropertyFixedValue(paramName, label, value, values, nodeId.id)

  }

  final case class OverwrittenVariable(variableName: String, nodeId: String, paramName: Option[String])
      extends PartSubGraphCompilationError
      with InASingleNode

  object OverwrittenVariable {
    def apply(variableName: String, paramName: Option[String])(implicit nodeId: NodeId): PartSubGraphCompilationError =
      OverwrittenVariable(variableName, nodeId.id, paramName)
  }

  final case class InvalidVariableName(name: String, nodeId: String, paramName: Option[String])
      extends PartSubGraphCompilationError
      with InASingleNode

  object InvalidVariableName {
    def apply(variableName: String, paramName: Option[String])(implicit nodeId: NodeId): PartSubGraphCompilationError =
      InvalidVariableName(variableName, nodeId.id, paramName)
  }

  final case class FragmentOutputNotDefined(id: String, nodeIds: Set[String]) extends ProcessCompilationError

  final case class DuplicateFragmentInputParameter(paramName: String, nodeId: String)
      extends PartSubGraphCompilationError
      with InASingleNode

  final case class RequireValueFromEmptyFixedList(paramName: String, nodeIds: Set[String])
      extends PartSubGraphCompilationError

  final case class InitialValueNotPresentInPossibleValues(paramName: String, nodeIds: Set[String])
      extends PartSubGraphCompilationError

  final case class UnsupportedFixedValuesType(paramName: String, typ: String, nodeIds: Set[String])
      extends PartSubGraphCompilationError

  final case class UnknownFragmentOutput(id: String, nodeIds: Set[String]) extends ProcessCompilationError

  final case class DisablingManyOutputsFragment(fragmentNodeId: String)
      extends ProcessCompilationError
      with ScenarioGraphLevelError {
    override def nodeIds: Set[String] = Set(fragmentNodeId)
  }

  final case class DisablingNoOutputsFragment(fragmentNodeId: String)
      extends ProcessCompilationError
      with ScenarioGraphLevelError {
    override def nodeIds: Set[String] = Set(fragmentNodeId)
  }

  final case class UnknownFragment(id: String, nodeId: String) extends ProcessCompilationError with InASingleNode

  final case class InvalidFragment(id: String, nodeId: String) extends ProcessCompilationError with InASingleNode

  sealed trait DuplicateFragmentOutputNames extends ProcessCompilationError {
    val duplicatedVarName: String
  }

  final case class DuplicateFragmentOutputNamesInScenario(duplicatedVarName: String, nodeId: String)
      extends DuplicateFragmentOutputNames
      with InASingleNode

  final case class DuplicateFragmentOutputNamesInFragment(duplicatedVarName: String, nodeIds: Set[String])
      extends DuplicateFragmentOutputNames
      with ScenarioGraphLevelError

  final case class DictNotDeclared(dictId: String, nodeId: String, paramName: String)
      extends PartSubGraphCompilationError
      with InASingleNode

  final case class DictEntryWithLabelNotExists(
      dictId: String,
      label: String,
      possibleLabels: Option[List[String]],
      nodeId: String,
      paramName: String
  ) extends PartSubGraphCompilationError
      with InASingleNode

  final case class DictEntryWithKeyNotExists(
      dictId: String,
      key: String,
      possibleKeys: Option[List[String]],
      nodeId: String,
      paramName: String
  ) extends PartSubGraphCompilationError
      with InASingleNode

  final case class KeyWithLabelExpressionParsingError(
      keyWithLabel: String,
      message: String,
      paramName: String,
      nodeId: String
  ) extends PartSubGraphCompilationError
      with InASingleNode

  final case class CustomNodeError(nodeId: String, message: String, paramName: Option[String])
      extends ProcessCompilationError
      with InASingleNode

  object CustomNodeError {
    def apply(message: String, paramName: Option[String])(implicit nodeId: NodeId): CustomNodeError =
      CustomNodeError(nodeId.id, message, paramName)
  }

  final case class FatalUnknownError(message: String) extends ProcessCompilationError {
    override def nodeIds: Set[String] = Set()
  }

  final case class CannotCreateObjectError(message: String, nodeId: String)
      extends ProcessCompilationError
      with InASingleNode

  final case class ScenarioNameValidationError(message: String, description: String)
      extends ProcessCompilationError
      with ScenarioPropertiesError

  final case class SpecificDataValidationError(fieldName: String, message: String)
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

  final case class NodeIdValidationError(errorType: IdErrorType, id: String) extends IdError with InASingleNode {
    override protected def nodeId: String = id
  }

  sealed trait IdErrorType
  object EmptyValue                                                           extends IdErrorType
  object BlankId                                                              extends IdErrorType
  object LeadingSpacesId                                                      extends IdErrorType
  object TrailingSpacesId                                                     extends IdErrorType
  final case class IllegalCharactersId(illegalCharacterHumanReadable: String) extends IdErrorType

}
