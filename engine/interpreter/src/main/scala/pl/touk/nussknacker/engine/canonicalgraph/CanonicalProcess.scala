package pl.touk.nussknacker.engine.canonicalgraph

import cats.data.Validated.{Invalid, Valid}
import cats.data.{NonEmptyList, Validated, ValidatedNel}
import pl.touk.nussknacker.engine.api.MetaData
import pl.touk.nussknacker.engine.canonicalgraph.canonicalnode.CanonicalNode
import pl.touk.nussknacker.engine.graph.exceptionhandler.ExceptionHandlerRef
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.graph.node._
import pl.touk.nussknacker.engine.util.validated.ValidatedSyntax
import cats.instances.list._

sealed trait CanonicalTreeNode

object CanonicalProcess {

  private def isNodeDisabled(node: CanonicalNode): Boolean =
    node.data match {
      case nodeData: Disableable if nodeData.isDisabled.contains(true) => true
      case _ => false
    }

  private def withoutDisabled(nodes: List[CanonicalNode]): List[CanonicalNode] = nodes.flatMap {
    case flatNode: canonicalnode.FlatNode if isNodeDisabled(flatNode) =>
      Nil
    case filterNode: canonicalnode.FilterNode if isNodeDisabled(filterNode) =>
      Nil
    case subprocessNode: canonicalnode.Subprocess if isNodeDisabled(subprocessNode) =>
      if (subprocessNode.outputs.size == 1) {
        withoutDisabled(subprocessNode.outputs.values.head)
      } else {
        throw new Exception("Fatal error. Disabled subprocess should be validated to have exactly one output")
      }
    case filterNode: canonicalnode.FilterNode =>
      List(
        filterNode.copy(nextFalse = withoutDisabled(filterNode.nextFalse))
      )
    case switchNode: canonicalnode.SwitchNode =>
      List(
        switchNode.copy(
          defaultNext = withoutDisabled(switchNode.defaultNext),
          nexts = switchNode.nexts.map { caseNode =>
            caseNode.copy(nodes = withoutDisabled(caseNode.nodes))
          }.filterNot(_.nodes.isEmpty)
        )
      )
    case splitNode: canonicalnode.SplitNode =>
      List(
        splitNode.copy(nexts = splitNode.nexts.map(withoutDisabled).filterNot(_.isEmpty))
      )
    case subprocessNode: canonicalnode.Subprocess =>
      List(
        subprocessNode.copy(
          outputs = subprocessNode.outputs.map { case (id, canonicalNodes) =>
            (id, withoutDisabled(canonicalNodes))
          }.filterNot { case (_, canonicalNodes) => canonicalNodes.isEmpty }
        )
      )
    case node =>
      List(node)
  }
}

//in fact with branches/join this form is not canonical anymore - graph can be represented in more than way
case class CanonicalProcess(metaData: MetaData,
                           //TODO: this makes sense only for StreamProcess, it should be moved to StreamMetadata
                           //not so easy to do, as it has classes from interprete and StreamMetadata is in API
                            exceptionHandlerRef: ExceptionHandlerRef,
                            //separation of nodes and additionalBranches is just for compatibility of stored json
                            //DON'T use these fields, rely on allStartNodes or mapAllNodes instead.
                            nodes: List[CanonicalNode],
                            additionalBranches: List[List[CanonicalNode]] = List.empty
                           ) extends CanonicalTreeNode {
  import CanonicalProcess._

  def allStartNodes: NonEmptyList[List[CanonicalNode]] = NonEmptyList(nodes, additionalBranches)

  def mapAllNodes(action: List[CanonicalNode] => List[CanonicalNode]): CanonicalProcess = withNodes(allStartNodes.map(action))

  def withNodes(nodes: NonEmptyList[List[CanonicalNode]]): CanonicalProcess = {
    val NonEmptyList(head, tail) = nodes
    copy(nodes = head, additionalBranches = tail)
  }

  lazy val withoutDisabledNodes: CanonicalProcess = mapAllNodes(withoutDisabled)

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
