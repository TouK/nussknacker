package pl.touk.esp.engine.traverse

import pl.touk.esp.engine.graph.node._

object NodesCollector {

  def collectNodes(node: Node): List[Node] = {
    val children = node match {
      case Source(_, _, next) =>
        collectNodes(next)
      case VariableBuilder(_, _, _, next) =>
        collectNodes(next)
      case Processor(_, _, next) =>
        collectNodes(next)
      case Enricher(_, _, _, next) =>
        collectNodes(next)
      case Filter(_, _, nextTrue, nextFalse) =>
        collectNodes(nextTrue) ::: nextFalse.toList.flatMap(collectNodes)
      case Switch(_, _, _, nexts, defaultNext) =>
        nexts.flatMap {
          case Case(_, n) => collectNodes(n)
        } ::: defaultNext.toList.flatMap(collectNodes)
      case Sink(_, _, _) =>
        Nil
    }
    node :: children
  }

}
