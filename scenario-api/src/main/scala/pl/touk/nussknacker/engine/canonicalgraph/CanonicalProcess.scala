package pl.touk.nussknacker.engine.canonicalgraph

import cats.data.NonEmptyList
import io.circe.{Decoder, Encoder}
import pl.touk.nussknacker.engine.api.MetaData
import pl.touk.nussknacker.engine.api.graph.ScenarioGraph
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.canonicalgraph.canonicalnode.CanonicalNode
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.graph.node
import pl.touk.nussknacker.engine.graph.node._
import pl.touk.nussknacker.engine.marshall.ProcessMarshaller

import scala.language.implicitConversions

sealed trait CanonicalTreeNode

object CanonicalProcess {

  // On the FE side, scenario properties are treated as a special type of node, so field name have to be the same
  val NameFieldName: String = node.IdFieldName

  private def isNodeDisabled(node: CanonicalNode): Boolean =
    node.data match {
      case nodeData: Disableable if nodeData.isDisabled.contains(true) => true
      case _                                                           => false
    }

  private def withoutDisabled(nodes: List[CanonicalNode]): List[CanonicalNode] = nodes.flatMap {
    case flatNode: canonicalnode.FlatNode if isNodeDisabled(flatNode) =>
      Nil
    case filterNode: canonicalnode.FilterNode if isNodeDisabled(filterNode) =>
      Nil
    case fragmentNode: canonicalnode.Fragment if isNodeDisabled(fragmentNode) =>
      if (fragmentNode.outputs.size == 1) {
        withoutDisabled(fragmentNode.outputs.values.head)
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
          nexts = switchNode.nexts
            .map { caseNode =>
              caseNode.copy(nodes = withoutDisabled(caseNode.nodes))
            }
            .filterNot(_.nodes.isEmpty)
        )
      )
    case splitNode: canonicalnode.SplitNode =>
      List(
        splitNode.copy(nexts = splitNode.nexts.map(withoutDisabled).filterNot(_.isEmpty))
      )
    case fragmentNode: canonicalnode.Fragment =>
      List(
        fragmentNode.copy(
          outputs = fragmentNode.outputs
            .map { case (id, canonicalNodes) =>
              (id, withoutDisabled(canonicalNodes))
            }
            .filterNot { case (_, canonicalNodes) => canonicalNodes.isEmpty }
        )
      )
    case custom: canonicalnode.CustomNodeWithOutputs =>
      val outputsWithoutDisabled = custom.outputs.toList
        .map(output => output.copy(nodes = withoutDisabled(output.nodes)))
        .filterNot(_.nodes.isEmpty)
      List(canonicalnode.CustomNodeWithOutputs(custom.data, outputsWithoutDisabled))
    case node =>
      List(node)
  }

  implicit lazy val canonicalProcessEncoder: Encoder[CanonicalProcess] = ProcessMarshaller.canonicalProcessEncoder

  implicit lazy val canonicalProcessDecoder: Decoder[CanonicalProcess] = ProcessMarshaller.canonicalProcessDecoder

}

//in fact with branches/join this form is not canonical anymore - graph can be represented in more than way
case class CanonicalProcess(
    metaData: MetaData,
    // separation of nodes and additionalBranches is just for compatibility of stored json
    // DON'T use these fields, rely on allStartNodes or mapAllNodes instead.
    nodes: List[CanonicalNode],
    additionalBranches: List[List[CanonicalNode]] = List.empty,
    stickyNotes: List[StickyNote] = List.empty,
) extends CanonicalTreeNode {

  import CanonicalProcess._

  def name: ProcessName = metaData.name

  def allStartNodes: NonEmptyList[List[CanonicalNode]] = NonEmptyList(nodes, additionalBranches)

  def mapAllNodes(action: List[CanonicalNode] => List[CanonicalNode]): CanonicalProcess = withNodes(
    allStartNodes.map(action)
  )

  def withNodes(nodes: NonEmptyList[List[CanonicalNode]]): CanonicalProcess = {
    val NonEmptyList(head, tail) = nodes
    copy(nodes = head, additionalBranches = tail)
  }

  def withProcessName(processName: ProcessName): CanonicalProcess =
    copy(metaData = metaData.copy(id = processName.value))

  lazy val withoutDisabledNodes: CanonicalProcess = mapAllNodes(withoutDisabled)

  def collectAllSources: List[SourceNodeData] = {
    val allNodes = collectAllNodes
    allNodes.flatMap(_.asSource) ++ allNodes.flatMap(_.asFragmentInputDefinition)
  }

  def collectAllNodes: List[NodeData] = allStartNodes.toList.flatten.flatMap(canonicalnode.collectAllNodes)

  def toScenarioGraph: ScenarioGraph = CanonicalProcessConverter.toScenarioGraph(this)

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

  case class Fragment(data: FragmentInput, outputs: Map[String, List[CanonicalNode]]) extends CanonicalNode

  case class Output(name: String, nodes: List[CanonicalNode]) extends CanonicalTreeNode

  /**
    * A custom node whose outputs, the main one included, are named subgraphs; nothing follows in the enclosing list
    * tail (accepted only as the last element of its branch, like `SwitchNode`). A custom node with no connected
    * outputs stays a `FlatNode`, the shape it has always had (no migration).
    */
  case class CustomNodeWithOutputs(data: CustomNode, outputs: NonEmptyList[Output]) extends CanonicalNode

  object CustomNodeWithOutputs {

    def apply(data: CustomNode, outputs: List[Output]): CanonicalNode =
      NonEmptyList.fromList(outputs).map(CustomNodeWithOutputs(data, _)).getOrElse(FlatNode(data))

  }

  def collectAllNodes(node: CanonicalNode): List[NodeData] = node match {
    case canonicalnode.FlatNode(data)              => List(data)
    case canonicalnode.FilterNode(data, nextFalse) => data :: nextFalse.flatMap(collectAllNodes)
    case canonicalnode.SwitchNode(data, nexts, defaultNext) =>
      data :: nexts.flatMap(_.nodes).flatMap(collectAllNodes) ::: defaultNext.flatMap(collectAllNodes)
    case canonicalnode.SplitNode(data, nexts)  => data :: nexts.flatten.flatMap(collectAllNodes)
    case canonicalnode.Fragment(data, outputs) => data :: outputs.values.flatten.toList.flatMap(collectAllNodes)
    case canonicalnode.CustomNodeWithOutputs(data, outputs) =>
      data :: outputs.toList.flatMap(_.nodes).flatMap(collectAllNodes)
  }

  /**
    * Rebuilds the node with `f` applied to every child branch list, leaving the node's own data untouched. Keeps the
    * exhaustive `CanonicalNode` match in one place, so a transformation recursing into branches (e.g. a scenario
    * migration) spells out only the cases it actually rewrites.
    */
  def mapBranches(node: CanonicalNode)(f: List[CanonicalNode] => List[CanonicalNode]): CanonicalNode = node match {
    case flat: canonicalnode.FlatNode              => flat
    case canonicalnode.FilterNode(data, nextFalse) => canonicalnode.FilterNode(data, f(nextFalse))
    case canonicalnode.SwitchNode(data, nexts, defaultNext) =>
      canonicalnode.SwitchNode(data, nexts.map(c => c.copy(nodes = f(c.nodes))), f(defaultNext))
    case canonicalnode.SplitNode(data, nexts) => canonicalnode.SplitNode(data, nexts.map(f))
    case canonicalnode.Fragment(data, outputs) =>
      canonicalnode.Fragment(data, outputs.map { case (name, out) => name -> f(out) })
    case canonicalnode.CustomNodeWithOutputs(data, outputs) =>
      canonicalnode.CustomNodeWithOutputs(data, outputs.map(o => o.copy(nodes = f(o.nodes))))
  }

}
