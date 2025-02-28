package pl.touk.nussknacker.engine.compiledgraph

import pl.touk.nussknacker.engine.compiledgraph.node._

//NOTE: logic of collector should match logic in ProcessSplitter
object CompiledNodesCollector {

  def collectNodes(node: Node): List[Node] = {
    val children = node match {
      case n: Source          => collectNodes(n.next)
      case n: VariableBuilder => collectNodes(n.next)
      case n: Processor       => collectNodes(n.next)
      case n: Enricher        => collectNodes(n.next)
      case n: Filter =>
        n.nextTrue.toList.flatMap(collectNodes) ::: n.nextFalse.toList.flatMap(collectNodes)
      case n: Switch =>
        n.nexts.flatMap { case Case(_, ch) =>
          collectNodes(ch)
        } ::: n.defaultNext.toList.flatMap(collectNodes)
      case n: CustomNode                => collectNodes(n.next)
      case n: FragmentUsageStart        => collectNodes(n.next)
      case n: FragmentUsageEnd          => collectNodes(n.next)
      case SplitNode(_, nextsWithParts) => nextsWithParts.flatMap(collectNodes)
      case _: Sink                      => List.empty
      case _: BranchEnd                 => List.empty
      case _: EndingCustomNode          => List.empty
      case _: EndingProcessor           => List.empty
      case _: FragmentOutput            => List.empty
    }
    node :: children
  }

  private def collectNodes(next: Next): List[Node] =
    next match {
      case NextNode(node) => collectNodes(node)
      case _: PartRef     => List.empty
    }

}
