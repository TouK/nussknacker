package pl.touk.esp.engine.traverse

import pl.touk.esp.engine.graph.node._

object NodesCollector {

  def collectNodes(node: Node): List[Node] = collectNodes(stopOnParts = true)(node)

  def collectNodesInAllParts(node: Node): List[Node] = collectNodes(stopOnParts = false)(node)

  private def collectNodes(stopOnParts: Boolean)(node: Node): List[Node] = {
    val collectNodesWithFlag = collectNodes(stopOnParts) _

    val children = node match {
      case Source(_, _, next) =>
        collectNodesWithFlag(next)
      case VariableBuilder(_, _, _, next) =>
        collectNodesWithFlag(next)
      case Processor(_, _, next) =>
        collectNodesWithFlag(next)
      case Enricher(_, _, _, next) =>
        collectNodesWithFlag(next)
      case Filter(_, _, nextTrue, nextFalse) =>
        collectNodesWithFlag(nextTrue) ::: nextFalse.toList.flatMap(collectNodesWithFlag)
      case Switch(_, _, _, nexts, defaultNext) =>
        nexts.flatMap {
          case Case(_, n) => collectNodesWithFlag(n)
        } ::: defaultNext.toList.flatMap(collectNodesWithFlag)
      case Sink(_, _, _) =>
        Nil
      case Aggregate(_, _, _, _, next) => if (stopOnParts) Nil else collectNodesWithFlag(next)
    }
    node :: children
  }

}
