package pl.touk.esp.engine.split

import pl.touk.esp.engine.splittedgraph.part._
import pl.touk.esp.engine.splittedgraph.splittednode._

object NodesCollector {

  def collectNodesInAllParts(part: ProcessPart): List[SplittedNode] =
    part match {
      case source: SourcePart =>
        collectNodes(source.source) ::: source.nextParts.flatMap(collectNodesInAllParts)
      case agg: AggregatePart =>
        collectNodes(agg.aggregate) ::: agg.nextParts.flatMap(collectNodesInAllParts)
      case sink: SinkPart =>
        collectNodes(sink.sink)
    }

  private def collectNodes(node: SplittedNode): List[SplittedNode] = {
    val children = node match {
      case n: Source =>
        collectNodes(n.next)
      case n: VariableBuilder =>
        collectNodes(n.next)
      case n: Processor =>
        collectNodes(n.next)
      case n: Enricher =>
        collectNodes(n.next)
      case n: Filter =>
        collectNodes(n.nextTrue) ::: n.nextFalse.toList.flatMap(collectNodes)
      case n: Switch =>
        n.nexts.flatMap {
          case Case(_, ch) => collectNodes(ch)
        } ::: n.defaultNext.toList.flatMap(collectNodes)
      case n: Aggregate =>
        collectNodes(n.next)
      case n: Sink =>
        List.empty
      case n: EndingProcessor =>
        List.empty
    }
    node :: children
  }

  private def collectNodes(next: Next): List[SplittedNode] =
    next match {
      case NextNode(node) => collectNodes(node)
      case part: PartRef => List.empty
    }

}
