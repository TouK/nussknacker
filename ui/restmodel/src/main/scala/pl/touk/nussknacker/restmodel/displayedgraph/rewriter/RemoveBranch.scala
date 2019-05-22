package pl.touk.nussknacker.restmodel.displayedgraph.rewriter

import pl.touk.nussknacker.engine.graph.node.NodeData
import pl.touk.nussknacker.restmodel.displayedgraph.DisplayableProcess
import pl.touk.nussknacker.restmodel.displayedgraph.displayablenode.Edge

class RemoveBranch(process: DisplayableProcess, branchRoot: NodeData) {

  def apply: DisplayableProcess = {
    val edgesToRemove = outgoingEdges
      .foldLeft(incomingEdges) { (edges, edge) =>
        edges ++ (edge :: subsequentEdges(edge))
      }

    val nodeIdsToRemove =
      branchRoot.id :: edgesToRemove.map(_.to)

    process.copy(
      edges = process.edges.filterNot(edgesToRemove.contains(_)),
      nodes = process.nodes.filterNot { n => nodeIdsToRemove.contains(n.id) }
    )
  }

  private def incomingEdges: List[Edge] = process.edges.filter(_.to == branchRoot.id)

  private def outgoingEdges: List[Edge] = process.edges.filter(_.from == branchRoot.id)

  private def subsequentEdges(edge: Edge): List[Edge] = {
    def subsequentEdges(agg: List[Edge], edge: Edge): List[Edge] =
      process.edges.filter(edge.to == _.from) match {
        case Nil =>
          edge :: agg
        case nextEdges =>
          (edge :: agg) ++ nextEdges.flatMap(subsequentEdges(agg, _))
      }

    subsequentEdges(List.empty, edge)
  }
}