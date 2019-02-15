package pl.touk.nussknacker.engine.split

import cats.data.NonEmptyList
import pl.touk.nussknacker.engine.graph.node.NodeData
import pl.touk.nussknacker.engine.splittedgraph.part._
import pl.touk.nussknacker.engine.splittedgraph.splittednode._

object NodesCollector {

  def collectNodesInAllParts(parts: NonEmptyList[SourcePart]): List[SplittedNode[_]]
    = parts.toList.flatMap(collectNodesInAllParts)

  def collectNodesInAllParts(part: ProcessPart): List[SplittedNode[_<:NodeData]] =
    part match {
      case source: SourcePart =>
        collectNodes(source.node) ::: source.nextParts.flatMap(collectNodesInAllParts)
      case sink: SinkPart =>
        collectNodes(sink.node)
      case custom:CustomNodePart =>
        collectNodes(custom.node) ::: custom.nextParts.flatMap(collectNodesInAllParts)
      case split: SplitPart =>
        collectNodes(split.node) ::: split.node.nexts.flatMap(_.nextParts).flatMap(collectNodesInAllParts)

    }

  private def collectNodes(node: SplittedNode[_<:NodeData]): List[SplittedNode[_<:NodeData]] = {
    val children = node match {
      case n: OneOutputNode[_] =>
        collectNodes(n.next)
      case n: FilterNode =>
        collectNodes(n.nextTrue) ::: n.nextFalse.toList.flatMap(collectNodes)
      case n: SwitchNode =>
        n.nexts.flatMap {
          case Case(_, ch) => collectNodes(ch)
        } ::: n.defaultNext.toList.flatMap(collectNodes)
      case SplitNode(_, nextsWithParts) =>
        nextsWithParts.map(_.next).flatMap(collectNodes)
      case n: EndingNode[_] =>
        List.empty
    }
    node :: children
  }

  private def collectNodes(next: Next): List[SplittedNode[_<:NodeData]] =
    next match {
      case NextNode(node) => collectNodes(node)
      case part: PartRef => List.empty
    }

}