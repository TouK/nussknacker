package pl.touk.nussknacker.engine.canonicalgraph

import pl.touk.nussknacker.engine.api.MetaData
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.{NodeExpressionId, NodeId}
import pl.touk.nussknacker.engine.canonicalgraph.canonicalnode._
import pl.touk.nussknacker.engine.compile.NodeTypingInfo
import pl.touk.nussknacker.engine.graph.evaluatedparam.{BranchParameters, Parameter}
import pl.touk.nussknacker.engine.graph.exceptionhandler.ExceptionHandlerRef
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.graph.node
import pl.touk.nussknacker.engine.graph.node.{BranchEndData, Enricher, NodeData, Source, Split, SubprocessInputDefinition, SubprocessOutput, SubprocessOutputDefinition}
import pl.touk.nussknacker.engine.graph.variable.Field

trait ProcessRewriter {

  def rewriteProcess(canonicalProcess: CanonicalProcess): CanonicalProcess = {
    implicit val metaData: MetaData = canonicalProcess.metaData
    canonicalProcess.copy(
      exceptionHandlerRef = rewriteExpressionHandler.applyOrElse(canonicalProcess.exceptionHandlerRef, identity[ExceptionHandlerRef]),
      nodes = rewriteNodes(canonicalProcess.nodes))
  }

  private def rewriteNodes(nodes: List[CanonicalNode])
                          (implicit metaData: MetaData) = nodes.map(rewriteSingleNode)

  private def rewriteSingleNode(node: CanonicalNode)
                               (implicit metaData: MetaData): CanonicalNode = {
    def rewriteIfMatching[T <: NodeData](data: T): T = rewriteNode.applyOrElse(data, identity[T]).asInstanceOf[T]
    node match {
      case FlatNode(data) =>
        FlatNode(
          rewriteIfMatching(data))
      case FilterNode(data, nextFalse) =>
        FilterNode(
          rewriteIfMatching(data),
          nextFalse.map(rewriteSingleNode))
      case SwitchNode(data, nexts, default) =>
        SwitchNode(
          rewriteIfMatching(data),
          nexts.map(cas => cas.copy(nodes = rewriteNodes(cas.nodes))),
          default.map(rewriteSingleNode))
      case SplitNode(data, nodes) =>
        SplitNode(
          rewriteIfMatching(data),
          nodes.map(rewriteNodes))
      case Subprocess(data, outputs) =>
        Subprocess(
          rewriteIfMatching(data),
          outputs.mapValues(rewriteNodes))
    }
  }

  protected def rewriteExpressionHandler(implicit metaData: MetaData): PartialFunction[ExceptionHandlerRef, ExceptionHandlerRef]

  protected def rewriteNode(implicit metaData: MetaData): PartialFunction[NodeData, NodeData]

}

object ProcessRewriter {

  def rewritingAllExpressions(rewrite: ExpressionIdWithMetaData => Expression => Expression): ProcessRewriter = {
    val exprRewriter = new ExpressionRewriter {
      override protected def rewriteExpression(e: Expression)(implicit expressionIdWithMetaData: ExpressionIdWithMetaData): Expression =
        rewrite(expressionIdWithMetaData)(e)
    }
    new ProcessRewriter {
      override protected def rewriteExpressionHandler(implicit metaData: MetaData): PartialFunction[ExceptionHandlerRef, ExceptionHandlerRef] = {
        case exceptionHandlerRef => exprRewriter.rewriteExpressionHandler(exceptionHandlerRef)
      }
      override protected def rewriteNode(implicit metaData: MetaData): PartialFunction[NodeData, NodeData] = {
        case data => exprRewriter.rewriteNode(data)
      }
    }
  }

}

trait ExpressionRewriter {

  def rewriteExpressionHandler(exceptionHandlerRef: ExceptionHandlerRef)(implicit metaData: MetaData): ExceptionHandlerRef = {
    implicit val nodeId: NodeId = NodeId(NodeTypingInfo.ExceptionHandlerNodeId)
    exceptionHandlerRef.copy(parameters = rewriteParameters(exceptionHandlerRef.parameters))
  }

  def rewriteNode(data: NodeData)(implicit metaData: MetaData): NodeData = {
    implicit val nodeId: NodeId = NodeId(data.id)
    data match {
      case n: node.Join =>
        n.copy(
          parameters = rewriteParameters(n.parameters),
          branchParameters = rewriteBranchParameters(n.branchParameters))
      case n: node.CustomNode =>
        n.copy(
          parameters = rewriteParameters(n.parameters))
      case n: node.VariableBuilder =>
        n.copy(
          fields = rewriteFields(n.fields))
      case n: node.Variable =>
        n.copy(
          value = rewriteDefaultExpressionInternal(n.value))
      case n: Enricher =>
        n.copy(
          service = n.service.copy(parameters = rewriteParameters(n.service.parameters)))
      case n: node.Processor =>
        n.copy(
          service = n.service.copy(parameters = rewriteParameters(n.service.parameters)))
      case n: node.Sink =>
        n.copy(
          ref = n.ref.copy(parameters = rewriteParameters(n.ref.parameters)),
          endResult = n.endResult.map(rewriteDefaultExpressionInternal))
      case n: node.SubprocessInput =>
        n.copy(
          ref = n.ref.copy(parameters = rewriteParameters(n.ref.parameters)))
      case n: node.Filter =>
        n.copy(
          expression = rewriteDefaultExpressionInternal(n.expression))
      case n: node.Switch =>
        n.copy(
          expression = rewriteDefaultExpressionInternal(n.expression))
      case n: Source =>
        n.copy(
          ref = n.ref.copy(parameters = rewriteParameters(n.ref.parameters)))
      case _: BranchEndData | _: Split | _: SubprocessInputDefinition | _: SubprocessOutputDefinition | _: SubprocessOutput => data
    }
  }

  private def rewriteFields(list: List[Field])(implicit metaData: MetaData, nodeId: NodeId): List[Field] =
    list.map(f => f.copy(expression = rewriteExpressionInternal(f.expression, f.name)))

  private def rewriteBranchParameters(list: List[BranchParameters])(implicit metaData: MetaData, nodeId: NodeId): List[BranchParameters] =
    list.map(bp => bp.copy(
      parameters = bp.parameters.map(p =>
        p.copy(expression = rewriteExpressionInternal(p.expression, NodeTypingInfo.branchParameterExpressionId(p.name, bp.branchId))))))

  private def rewriteParameters(list: List[Parameter])(implicit metaData: MetaData, nodeId: NodeId): List[Parameter] =
    list.map(p => p.copy(expression = rewriteExpressionInternal(p.expression, p.name)))

  private def rewriteDefaultExpressionInternal(e: Expression)
                                              (implicit metaData: MetaData, nodeId: NodeId): Expression =
    rewriteExpressionInternal(e, NodeTypingInfo.DefaultExpressionId)

  private def rewriteExpressionInternal(e: Expression, expressionId: String)
                                       (implicit metaData: MetaData, nodeId: NodeId): Expression =
    rewriteExpression(e)(ExpressionIdWithMetaData(metaData, NodeExpressionId(nodeId, expressionId)))

  protected def rewriteExpression(e: Expression)(implicit expressionIdWithMetaData: ExpressionIdWithMetaData): Expression

}

case class ExpressionIdWithMetaData(metaData: MetaData, expressionId: NodeExpressionId)