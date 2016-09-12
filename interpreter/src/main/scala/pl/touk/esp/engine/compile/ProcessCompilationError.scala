package pl.touk.esp.engine.compile

sealed trait ProcessCompilationError {
  def nodeIds: Set[String]
}

sealed trait ProcessUncanonizationError extends ProcessCompilationError

sealed trait PartSubGraphCompilationError extends ProcessCompilationError

object ProcessCompilationError {

  final val ProcessNodeId = "$process"

  case class NodeId(id: String)

  trait InASingleNode { self: ProcessCompilationError =>

    override def nodeIds: Set[String] = Set(nodeId)

    protected def nodeId: String

  }

  case class InvaliRootNode(nodeId: String) extends ProcessUncanonizationError with InASingleNode

  case class InvalidTailOfBranch(nodeIds: Set[String]) extends ProcessUncanonizationError

  case class DuplicatedNodeIds(nodeIds: Set[String]) extends ProcessCompilationError

  case class NotSupportedExpressionLanguage(languageId: String, nodeId: String)
    extends PartSubGraphCompilationError with InASingleNode

  object NotSupportedExpressionLanguage {
    def apply(languageId: String)(implicit nodeId: NodeId): PartSubGraphCompilationError =
      NotSupportedExpressionLanguage(languageId, nodeId.id)
  }

  case class ExpressionParseError(message: String, nodeId: String, originalExpr: String)
    extends PartSubGraphCompilationError with InASingleNode

  object ExpressionParseError {
    def apply(message: String, originalExpr: String)(implicit nodeId: NodeId): PartSubGraphCompilationError =
      ExpressionParseError(message, nodeId.id, originalExpr)
  }

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

  case class MissingFoldingFunction(name: String, nodeId: String)
    extends ProcessCompilationError with InASingleNode

  object MissingFoldingFunction {
    def apply(name: String)(implicit nodeId: NodeId): ProcessCompilationError =
      MissingFoldingFunction(name, nodeId.id)
  }

  case class MissingParameters(params: Set[String], nodeId: String)
    extends PartSubGraphCompilationError with InASingleNode

  object MissingParameters {
    def apply(params: Set[String])(implicit nodeId: NodeId): ProcessCompilationError =
      MissingParameters(params, nodeId.id)
  }

  case class RedundantParameters(params: Set[String], nodeId: String)
    extends PartSubGraphCompilationError with InASingleNode

  object RedundantParameters {
    def apply(params: Set[String])(implicit nodeId: NodeId): PartSubGraphCompilationError =
      RedundantParameters(params, nodeId.id)
  }

}
