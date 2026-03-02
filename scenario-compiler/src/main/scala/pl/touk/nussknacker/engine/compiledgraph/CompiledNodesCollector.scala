package pl.touk.nussknacker.engine.compiledgraph

import pl.touk.nussknacker.engine.compiledgraph.node._

object CompiledNodesCollector {

  def collectAllNodes(node: Node): List[Node] = {
    val children = node match {
      case n: Source          => collectNodes(n.next)
      case n: VariableBuilder => collectNodes(n.next)
      case n: Processor       => collectNodes(n.next)
      case n: Enricher        => collectNodes(n.next)
      case n: Filter =>
        collectNodes(n.nextTrue) ::: collectNodes(n.nextFalse)
      case n: Switch =>
        n.nexts.flatMap { case Case(_, ch) =>
          collectNodes(ch)
        } ::: collectNodes(n.defaultNext)
      case n: CustomNode                   => collectNodes(n.next)
      case n: FragmentUsageStart           => collectNodes(n.next)
      case n: FragmentUsageEnd             => collectNodes(n.next)
      case SplitNode(_, _, nextsWithParts) => nextsWithParts.flatMap(next => collectNodes(Some(next)))
      case _: Sink                         => List.empty
      case _: BranchEnd                    => List.empty
      case _: EndingCustomNode             => List.empty
      case _: EndingProcessor              => List.empty
      case _: FragmentOutput               => List.empty
    }
    node :: children
  }

  private def collectNodes(next: Option[Next]): List[Node] =
    next match {
      case Some(NextNode(node)) => collectAllNodes(node)
      case Some(_: PartRef)     => List.empty
      case None                 => List.empty
    }

}
