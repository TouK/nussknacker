package pl.touk.nussknacker.engine.compile

import pl.touk.nussknacker.engine.graph.node.{BranchEndData, BranchEndDefinition, SourceNodeData}
import pl.touk.nussknacker.engine.splittedgraph.SplittedNodesCollector
import pl.touk.nussknacker.engine.splittedgraph.part.SourcePart
import pl.touk.nussknacker.engine.splittedgraph.splittednode.EndingNode

import scala.annotation.tailrec

object PartSort {

  @tailrec
  //this is primitive topological sort, we want to sort parts,
  //so that for each part starting with join J1, all branches pointing to J1 are before in final list
  def sort(partsToSort: List[SourcePart], sorted: List[SourcePart] = List()): List[SourcePart] = {
    if (partsToSort.isEmpty) {
      sorted
    } else {
      def readyPredicate(part: SourcePart): Boolean = part.node.data.isInstanceOf[SourceNodeData] || sourcePartIdNotInBranchEnds(part, partsToSort)
      val nextSorted = partsToSort.filter(readyPredicate)
      val rest = partsToSort.filterNot(readyPredicate)
      if (nextSorted.isEmpty) {
        //don't want endless loops, this should not happen... ;)
        throw new IllegalArgumentException(s"Should not happen, maybe there is cycle?, to sort: ${rest.map(_.id)}, sorted: ${sorted.map(_.id)}")
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
