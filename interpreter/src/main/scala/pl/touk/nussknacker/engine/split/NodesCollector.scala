package pl.touk.nussknacker.engine.split

import cats.data.NonEmptyList
import pl.touk.nussknacker.engine.graph.node.NodeData
import pl.touk.nussknacker.engine.splittedgraph.part._
import pl.touk.nussknacker.engine.splittedgraph.splittednode._
import pl.touk.nussknacker.engine.splittedgraph.SplittedNodesCollector.collectNodes

object NodesCollector {

  def collectNodesInAllParts(parts: NonEmptyList[ProcessPart]): List[SplittedNode[_<:NodeData]]
    = parts.toList.flatMap(collectNodesInAllParts)

  def collectNodesInAllParts(part: ProcessPart): List[SplittedNode[_<:NodeData]] =
    part match {
      case source: SourcePart =>
        collectNodes(source.node) ::: source.nextParts.flatMap(collectNodesInAllParts)
      case sink: SinkPart =>
        collectNodes(sink.node)
      case custom:CustomNodePart =>
        collectNodes(custom.node) ::: custom.nextParts.flatMap(collectNodesInAllParts)
    }

}
