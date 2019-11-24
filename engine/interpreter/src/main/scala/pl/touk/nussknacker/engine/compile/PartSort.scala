package pl.touk.nussknacker.engine.compile

import pl.touk.nussknacker.engine.graph.node.{BranchEndData, BranchEndDefinition, SourceNodeData}
import pl.touk.nussknacker.engine.splittedgraph.SplittedNodesCollector
import pl.touk.nussknacker.engine.splittedgraph.part.SourcePart
import pl.touk.nussknacker.engine.splittedgraph.splittednode.EndingNode

import scala.annotation.tailrec

object PartSort {

  @tailrec
  def sort(partsToSort: List[SourcePart], sorted: List[SourcePart] = List()): List[SourcePart] = {
    if (partsToSort.isEmpty) {
      sorted
    } else {
      val (nextSorted, rest)
        = partsToSort.span(part => part.node.data.isInstanceOf[SourceNodeData] || sourcePartIdNotInBranchEnds(part, partsToSort))
      if (nextSorted.isEmpty) {
        //don't want endless loops ;)
        throw new IllegalArgumentException("Should not happen, maybe there is cycle?")
      }
      sort(rest, sorted ++ nextSorted)
    }
  }

  private def sourcePartIdNotInBranchEnds(part: SourcePart, toSort: List[SourcePart]): Boolean = {
    toSort.map(_.node)
      .flatMap(SplittedNodesCollector.collectNodes)
      .collect {
        case EndingNode(BranchEndData(BranchEndDefinition(_, joinId))) if joinId == part.id =>
          true
      }.isEmpty
  }

}
