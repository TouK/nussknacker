package pl.touk.nussknacker.engine.splittedgraph

import pl.touk.nussknacker.engine.graph.node.NodeData
import pl.touk.nussknacker.engine.splittedgraph.splittednode.{Case, EndingNode, FilterNode, Next, NextNode, OneOutputNode, PartRef, SplitNode, SplittedNode, SwitchNode}

//NOTE: logic of collector should match logic in ProcessSplitter
object SplittedNodesCollector {

  def collectNodes(node: SplittedNode[_<:NodeData]): List[SplittedNode[_<:NodeData]] = {
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
        nextsWithParts.flatMap(collectNodes)
      case _: EndingNode[_] =>
        List.empty
    }
    node :: children
  }

  private def collectNodes(next: Next): List[SplittedNode[_<:NodeData]] =
    next match {
      case NextNode(node) => collectNodes(node)
      case _: PartRef => List.empty
  }

}
