package pl.touk.nussknacker.engine.canonicalgraph

import cats.data.NonEmptyList
import io.circe.{Decoder, Encoder}
import pl.touk.nussknacker.engine.api.MetaData
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.canonicalgraph.canonicalnode.CanonicalNode
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.graph.node._
import pl.touk.nussknacker.engine.marshall.ProcessMarshaller

import scala.language.implicitConversions

sealed trait CanonicalTreeNode

object CanonicalProcess {

  //TODO: replace EspProcess use with CanonicalProcess where needed
  //implicit def toCanonical(espProcess: EspProcess): CanonicalProcess = espProcess.toCanonicalProcess

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
        throw new Exception("Fatal error. Disabled scenario fragment should be validated to have exactly one output")
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

  implicit lazy val canonicalProcessEncoder: Encoder[CanonicalProcess] = ProcessMarshaller.canonicalProcessEncoder

  implicit lazy val canonicalProcessDecoder: Decoder[CanonicalProcess] = ProcessMarshaller.canonicalProcessDecoder

}

//in fact with branches/join this form is not canonical anymore - graph can be represented in more than way
case class CanonicalProcess(metaData: MetaData,
                            //separation of nodes and additionalBranches is just for compatibility of stored json
                            //DON'T use these fields, rely on allStartNodes or mapAllNodes instead.
                            nodes: List[CanonicalNode],
                            additionalBranches: List[List[CanonicalNode]] = List.empty
                           ) extends CanonicalTreeNode {
  import CanonicalProcess._

  lazy val id: String = metaData.id

  def allStartNodes: NonEmptyList[List[CanonicalNode]] = NonEmptyList(nodes, additionalBranches)

  def mapAllNodes(action: List[CanonicalNode] => List[CanonicalNode]): CanonicalProcess = withNodes(allStartNodes.map(action))

  def withNodes(nodes: NonEmptyList[List[CanonicalNode]]): CanonicalProcess = {
    val NonEmptyList(head, tail) = nodes
    copy(nodes = head, additionalBranches = tail)
  }

  def withProcessId(processName: ProcessName): CanonicalProcess =
    copy(metaData = metaData.copy(id = processName.value))

  lazy val withoutDisabledNodes: CanonicalProcess = mapAllNodes(withoutDisabled)

  def collectAllNodes: List[NodeData] = {
    def nextNodes(node: CanonicalNode): List[NodeData] = node match {
      case canonicalnode.FlatNode(data) => List(data)
      case canonicalnode.FilterNode(data, nextFalse) => data :: nextFalse.flatMap(nextNodes)
      case canonicalnode.SwitchNode(data, nexts, defaultNext) => data :: nexts.flatMap(_.nodes).flatMap(nextNodes) ::: defaultNext.flatMap(nextNodes)
      case canonicalnode.SplitNode(data, nexts) => data :: nexts.flatten.flatMap(nextNodes)
      case canonicalnode.Subprocess(data, outputs) => data :: outputs.values.flatten.toList.flatMap(nextNodes)
    }
    allStartNodes.toList.flatten.flatMap(nextNodes)
  }

  //TODO: remove & deprecate :) 
  val toCanonicalProcess: CanonicalProcess = this

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
