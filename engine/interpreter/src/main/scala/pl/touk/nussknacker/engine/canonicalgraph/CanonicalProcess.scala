package pl.touk.nussknacker.engine.canonicalgraph

import pl.touk.nussknacker.engine.api.MetaData
import pl.touk.nussknacker.engine.canonicalgraph.canonicalnode.{CanonicalNode, FlatNode}
import pl.touk.nussknacker.engine.graph.exceptionhandler.ExceptionHandlerRef
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.graph.node._

sealed trait CanonicalTreeNode

object CanonicalProcess {

  private def withoutDisabled(nodes: List[CanonicalNode]): List[CanonicalNode] =
    nodes.foldLeft(List.empty[CanonicalNode]) { (agg, node) =>
      node match {
        case flatNode: canonicalnode.FlatNode if isNodeDisabled(flatNode) =>
          agg
        case filterNode: canonicalnode.FilterNode if isNodeDisabled(filterNode) =>
          agg
        case subprocessNode: canonicalnode.Subprocess if isNodeDisabled(subprocessNode) =>
          subprocessNode.outputs.headOption match {
            case Some((_, subOutNodes)) => agg ++ subOutNodes
            case None => agg
          }
        case filterNode: canonicalnode.FilterNode =>
          agg ++ List(
            filterNode.copy(nextFalse = withoutDisabled(filterNode.nextFalse))
          )
        case switchNode: canonicalnode.SwitchNode =>
          agg ++ List(
            switchNode.copy(
              defaultNext = withoutDisabled(switchNode.defaultNext),
              nexts = switchNode.nexts.map { caseNode =>
                caseNode.copy(nodes = withoutDisabled(caseNode.nodes))
              }.filterNot(_.nodes.isEmpty)
            )
          )
        case splitNode: canonicalnode.SplitNode =>
          agg ++ List(
            splitNode.copy(nexts = splitNode.nexts.map(withoutDisabled).filterNot(_.isEmpty))
          )
        case subprocessNode: canonicalnode.Subprocess =>
          agg ++ List(
            subprocessNode.copy(
              outputs = subprocessNode.outputs.map { case (id, canonicalNodes) =>
                (id, withoutDisabled(canonicalNodes))
              }.filterNot { case (_, canonicalNodes) => canonicalNodes.isEmpty }
            )
          )
        case _ =>
          agg ++ List(node)
      }
    }

  private def isNodeDisabled(node: CanonicalNode): Boolean =
    node.data match {
      case nodeData: Disableable if nodeData.isDisabled.contains(true) => true
      case _ => false
    }
}

//in fact with branches/join this form is not canonical anymore - graph can be represented in more than way
case class CanonicalProcess(metaData: MetaData,
                           //TODO: this makes sense only for StreamProcess, it should be moved to StreamMetadata
                           //not so easy to do, as it has classes from interprete and StreamMetadata is in API
                            exceptionHandlerRef: ExceptionHandlerRef,
                            nodes: List[CanonicalNode],
                           //Separation from nodes and Option is for json backwards compatibility
                           //in the future this form will probably be removed
                            additionalBranches: Option[List[List[CanonicalNode]]]
                           ) extends CanonicalTreeNode {

  lazy val withoutDisabledNodes = copy(
    nodes = CanonicalProcess.withoutDisabled(nodes))
}

object canonicalnode {

  sealed trait CanonicalNode extends CanonicalTreeNode {
    def data: NodeData
    def id: String = data.id
  }

  case class FlatNode(data: NodeData) extends CanonicalNode

  case class FilterNode(data: Filter, nextFalse: List[CanonicalNode]) extends CanonicalNode

  case class SwitchNode(data: Switch, nexts: List[Case], defaultNext: List[CanonicalNode]) extends CanonicalNode

  case class SplitNode(data: Split, nexts: List[List[CanonicalNode]]) extends CanonicalNode

  case class Case(expression: Expression, nodes: List[CanonicalNode]) extends CanonicalTreeNode

  case class Subprocess(data: SubprocessInput,
                        outputs: Map[String, List[CanonicalNode]]) extends CanonicalNode

}