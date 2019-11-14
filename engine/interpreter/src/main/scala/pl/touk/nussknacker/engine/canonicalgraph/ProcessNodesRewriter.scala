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

import scala.reflect._

/**
  * Rewrites data of each node in process without changing the structure of process graph.
  */
trait ProcessNodesRewriter {

  def rewriteProcess(canonicalProcess: CanonicalProcess): CanonicalProcess = {
    implicit val metaData: MetaData = canonicalProcess.metaData
    canonicalProcess.copy(
      exceptionHandlerRef = rewriteExceptionHandler(canonicalProcess.exceptionHandlerRef).getOrElse(canonicalProcess.exceptionHandlerRef),
      nodes = rewriteNodes(canonicalProcess.nodes))
  }

  private def rewriteNodes(nodes: List[CanonicalNode])
                          (implicit metaData: MetaData) = nodes.map(rewriteSingleNode)

  private def rewriteSingleNode(node: CanonicalNode)
                               (implicit metaData: MetaData): CanonicalNode = {
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

  protected def rewriteExceptionHandler(exceptionHandlerRef: ExceptionHandlerRef)(implicit metaData: MetaData): Option[ExceptionHandlerRef]

  protected def rewriteIfMatching[T <: NodeData: ClassTag](data: T)(implicit metaData: MetaData): T = {
    val rewritten = rewriteNode(data).getOrElse(data)
    assume(rewritten.isInstanceOf[T], s"Result type of rewritten node's data: ${rewritten.getClass} is not a subtype of expected type: ${classTag[T].runtimeClass}")
    rewritten
  }

  /**
    * Rewrites node's data. Result type should be a subtype of T. Type parameter T depends on place in structure that is rewritten.
    * See `rewriteSingleNode` for implementation details.
    *
    * @param data node's data
    * @param metaData process metada
    * @tparam T required common supertype for input `data` and result
    * @return rewritten data that satisfy T
    */
  protected def rewriteNode[T <: NodeData: ClassTag](data: T)(implicit metaData: MetaData): Option[T]

}

object ProcessNodesRewriter {

  def rewritingAllExpressions(rewrite: ExpressionIdWithMetaData => Expression => Expression): ProcessNodesRewriter = {
    val exprRewriter = new ExpressionRewriter {
      override protected def rewriteExpression(e: Expression)(implicit expressionIdWithMetaData: ExpressionIdWithMetaData): Expression =
        rewrite(expressionIdWithMetaData)(e)
    }
    new ProcessNodesRewriter {
      override protected def rewriteExceptionHandler(exceptionHandlerRef: ExceptionHandlerRef)(implicit metaData: MetaData) =
        Some(exprRewriter.rewriteExpressionHandler(exceptionHandlerRef))

      override protected def rewriteNode[T <: NodeData: ClassTag](data: T)(implicit metaData: MetaData) =
        Some(exprRewriter.rewriteNode(data))
    }
  }

}

trait ExpressionRewriter {

  def rewriteExpressionHandler(exceptionHandlerRef: ExceptionHandlerRef)(implicit metaData: MetaData): ExceptionHandlerRef = {
    implicit val nodeId: NodeId = NodeId(NodeTypingInfo.ExceptionHandlerNodeId)
    exceptionHandlerRef.copy(parameters = rewriteParameters(exceptionHandlerRef.parameters))
  }

  def rewriteNode[T <: NodeData: ClassTag](data: T)(implicit metaData: MetaData): T = {
    implicit val nodeId: NodeId = NodeId(data.id)
    rewriteNodeInternal(data).asInstanceOf[T]
  }

  private def rewriteNodeInternal(data: NodeData)(implicit metaData: MetaData, nodeId: NodeId): NodeData =
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