package pl.touk.nussknacker.engine.api.context

import cats.Applicative
import cats.data.ValidatedNel
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.{InASingleNode, NodeId}

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

  // TODO: Move NodeId and NodeExpressionId to NodeTypingInfo or somewhere closer to process model
  case class NodeId(id: String)

  case class NodeExpressionId(nodeId: NodeId, expressionId: String)

  object NodeExpressionId {
    def apply(expressionId: String)(implicit nodeId: NodeId): NodeExpressionId =
      NodeExpressionId(nodeId, expressionId)
  }

  trait InASingleNode { self: ProcessCompilationError =>

    override def nodeIds: Set[String] = Set(nodeId)

    protected def nodeId: String

  }

  case class WrongProcessType() extends ProcessCompilationError {
    override def nodeIds = Set()
  }

  case class UnsupportedPart(nodeId: String) extends ProcessCompilationError with InASingleNode

  case class MissingPart(nodeId: String) extends ProcessCompilationError with InASingleNode

  case class InvalidRootNode(nodeId: String) extends ProcessUncanonizationError with InASingleNode

  object EmptyProcess extends ProcessUncanonizationError {
    override def nodeIds = Set()
  }

  case class InvalidTailOfBranch(nodeId: String) extends ProcessUncanonizationError with InASingleNode

  case class DuplicatedNodeIds(nodeIds: Set[String]) extends ProcessCompilationError

  case class NotSupportedExpressionLanguage(languageId: String, nodeId: String)
    extends PartSubGraphCompilationError with InASingleNode

  object NotSupportedExpressionLanguage {
    def apply(languageId: String)(implicit nodeId: NodeId): PartSubGraphCompilationError =
      NotSupportedExpressionLanguage(languageId, nodeId.id)
  }

  case class ExpressionParseError(message: String, nodeId: String, fieldName: Option[String], originalExpr: String)
    extends PartSubGraphCompilationError with InASingleNode

  object ExpressionParseError {
    def apply(message: String, fieldName: Option[String], originalExpr: String)(implicit nodeId: NodeId): PartSubGraphCompilationError =
      ExpressionParseError(message, nodeId.id, fieldName, originalExpr)
  }

  case class SubprocessParamClassLoadError(fieldName: String, refClazzName: String, nodeId: String)
    extends PartSubGraphCompilationError with InASingleNode

  case class MissingService(serviceId: String, nodeId: String)
    extends PartSubGraphCompilationError with InASingleNode

  object MissingService {
    def apply(serviceId: String)(implicit nodeId: NodeId): PartSubGraphCompilationError =
      MissingService(serviceId, nodeId.id)
  }

  case class MissingSourceFactory(typ: String, nodeId: String)
    extends ProcessCompilationError with InASingleNode

  object MissingSourceFactory {
    def apply(typ: String)(implicit nodeId: NodeId): ProcessCompilationError =
      MissingSourceFactory(typ, nodeId.id)
  }

  case class MissingSinkFactory(typ: String, nodeId: String)
    extends ProcessCompilationError with InASingleNode

  object MissingSinkFactory {
    def apply(typ: String)(implicit nodeId: NodeId): ProcessCompilationError =
      MissingSinkFactory(typ, nodeId.id)
  }

  case class MissingCustomNodeExecutor(name: String, nodeId: String)
    extends ProcessCompilationError with InASingleNode

  object MissingCustomNodeExecutor {
    def apply(name: String)(implicit nodeId: NodeId): ProcessCompilationError =
      MissingCustomNodeExecutor(name, nodeId.id)
  }
  
  case class MissingParameters(params: Set[String], nodeId: String)
    extends PartSubGraphCompilationError with InASingleNode

  case class UnresolvedSubprocess(nodeId: String)
    extends PartSubGraphCompilationError with InASingleNode


  object MissingParameters {
    def apply(params: Set[String])(implicit nodeId: NodeId): PartSubGraphCompilationError =
      MissingParameters(params, nodeId.id)
  }

  case class RedundantParameters(params: Set[String], nodeId: String)
    extends PartSubGraphCompilationError with InASingleNode

  object RedundantParameters {
    def apply(params: Set[String])(implicit nodeId: NodeId): PartSubGraphCompilationError =
      RedundantParameters(params, nodeId.id)
  }

  case class WrongParameters(requiredParameters: Set[String], passedParameters: Set[String], nodeId: String)
    extends PartSubGraphCompilationError with InASingleNode

  object WrongParameters {
    def apply(requiredParameters: Set[String], passedParameters: Set[String])(implicit nodeId: NodeId): PartSubGraphCompilationError =
      WrongParameters(requiredParameters, passedParameters, nodeId.id)
  }

  case class BlankParameter(message: String, description: String, paramName: String, nodeId: String) extends ParameterValidationError

  case class EmptyMandatoryParameter(message: String, description: String, paramName: String, nodeId: String) extends ParameterValidationError

  case class InvalidIntegerLiteralParameter(message: String, description: String, paramName: String, nodeId: String) extends ParameterValidationError

  case class MismatchParameter(message: String, description: String, paramName: String, nodeId: String) extends ParameterValidationError

  case class LowerThanRequiredParameter(message: String, description: String, paramName: String, nodeId: String) extends ParameterValidationError

  case class GreaterThanRequiredParameter(message: String, description: String, paramName: String, nodeId: String) extends ParameterValidationError

  case class JsonRequiredParameter(message: String, description: String, paramName: String, nodeId: String) extends ParameterValidationError

  case class CustomParameterValidationError(message: String, description: String, paramName: String, nodeId: String) extends ParameterValidationError

  case class MissingRequiredProperty(paramName: String, label: Option[String], nodeId: String)
    extends PartSubGraphCompilationError with InASingleNode

  object MissingRequiredProperty {
    def apply(paramName: String, label: Option[String])(implicit nodeId: NodeId): PartSubGraphCompilationError =
      MissingRequiredProperty(paramName, label, nodeId.id)
  }

  case class UnknownProperty(paramName: String, nodeId: String)
    extends PartSubGraphCompilationError with InASingleNode

  object UnknownProperty {
    def apply(paramName: String)(implicit nodeId: NodeId): PartSubGraphCompilationError =
      UnknownProperty(paramName, nodeId.id)
  }

  case class InvalidPropertyFixedValue(paramName: String, label: Option[String], value: String, values: List[String], nodeId: String)
    extends PartSubGraphCompilationError with InASingleNode

  object InvalidPropertyFixedValue {
    def apply(paramName: String, label: Option[String], value: String, values: List[String])(implicit nodeId: NodeId): PartSubGraphCompilationError =
      InvalidPropertyFixedValue(paramName, label, value, values, nodeId.id)
  }

  case class OverwrittenVariable(variableName: String, nodeId: String, paramName: Option[String])
    extends PartSubGraphCompilationError with InASingleNode

  object OverwrittenVariable {
    def apply(variableName: String, paramName: Option[String])(implicit nodeId: NodeId): PartSubGraphCompilationError =
      OverwrittenVariable(variableName, nodeId.id, paramName)
  }

  case class InvalidVariableOutputName(name: String, nodeId: String, paramName: Option[String]) extends PartSubGraphCompilationError with InASingleNode

  object InvalidVariableOutputName {
    def apply(variableName: String, paramName: Option[String])(implicit nodeId: NodeId): PartSubGraphCompilationError =
      InvalidVariableOutputName(variableName, nodeId.id, paramName)
  }

  case class NoParentContext(nodeId: String) extends PartSubGraphCompilationError with InASingleNode

  case class UnknownSubprocessOutput(id: String, nodeId: String) extends ProcessCompilationError with InASingleNode

  case class DisablingManyOutputsSubprocess(id: String, nodeIds: Set[String]) extends ProcessCompilationError

  case class DisablingNoOutputsSubprocess(id: String) extends ProcessCompilationError {
    override def nodeIds: Set[String] = Set.empty
  }
  case class UnknownSubprocess(id: String, nodeId: String) extends ProcessCompilationError with InASingleNode

  case class InvalidSubprocess(id: String, nodeId: String) extends ProcessCompilationError with InASingleNode

  case class CustomNodeError(nodeId: String, message: String, paramName: Option[String]) extends ProcessCompilationError with InASingleNode

  object CustomNodeError {
    def apply(message: String, paramName: Option[String])(implicit nodeId: NodeId): CustomNodeError = CustomNodeError(nodeId.id, message, paramName)
  }

  case class FatalUnknownError(message: String) extends ProcessCompilationError {
    override def nodeIds: Set[String] = Set()
  }

  case class CannotCreateObjectError(message: String, nodeId: String) extends ProcessCompilationError with InASingleNode
}
