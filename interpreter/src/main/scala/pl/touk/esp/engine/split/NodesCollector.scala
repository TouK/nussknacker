package pl.touk.esp.engine.split

import pl.touk.esp.engine.splittedgraph.part._
import pl.touk.esp.engine.splittedgraph.splittednode._

object NodesCollector {

  def collectNodesInAllParts(part: ProcessPart): List[SplittedNode[_]] =
    part match {
      case source: SourcePart =>
        collectNodes(source.node) ::: source.nextParts.flatMap(collectNodesInAllParts)
      case sink: SinkPart =>
        collectNodes(sink.node)
      case custom:CustomNodePart =>
        collectNodes(custom.node) ::: custom.nextParts.flatMap(collectNodesInAllParts)

    }

  private def collectNodes(node: SplittedNode[_]): List[SplittedNode[_]] = {
    val children = node match {
      case n: OneOutputNode[_] =>
        collectNodes(n.next)
      case n: FilterNode =>
        collectNodes(n.nextTrue) ::: n.nextFalse.toList.flatMap(collectNodes)
      case n: SwitchNode =>
        n.nexts.flatMap {
          case Case(_, ch) => collectNodes(ch)
        } ::: n.defaultNext.toList.flatMap(collectNodes)
      case n: EndingNode[_] =>
        List.empty
    }
    node :: children
  }

  private def collectNodes(next: Next): List[SplittedNode[_]] =
    next match {
      case NextNode(node) => collectNodes(node)
      case part: PartRef => List.empty
    }

}