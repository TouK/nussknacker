package pl.touk.nussknacker.restmodel.displayedgraph.rewriter

import pl.touk.nussknacker.engine.graph.node.{NodeData, Sink}
import pl.touk.nussknacker.restmodel.displayedgraph.{DisplayableProcess, ProcessRewriter}
import pl.touk.nussknacker.restmodel.displayedgraph.displayablenode.Edge
import pl.touk.nussknacker.restmodel.displayedgraph.displayablenode.EdgeType.FilterFalse


object RemoveAndBypassNode {
  private case class NodeEdges(incoming: List[Edge], outgoing: List[Edge])
}

class RemoveAndBypassNode(process: DisplayableProcess, node: NodeData) {
  import RemoveAndBypassNode._

  def apply: DisplayableProcess = {
    val nodeEdges = getNodeEdges(node)

    getBranchRootToRemove(nodeEdges) match {
      case Some(branchRoot) =>
        val processWithRemovedBranches = ProcessRewriter.removeBranch(process, branchRoot)
        new RemoveAndBypassNode(processWithRemovedBranches, node).apply

      case None =>
        val maybeBypassEdge = getBypassEdge(nodeEdges)

        val newEdges = maybeBypassEdge.map(List(_)).getOrElse(List.empty) ++
          process.edges.filterNot { disabledEdge =>
            (nodeEdges.incoming ++ nodeEdges.outgoing).contains(disabledEdge)
          }

        process.copy(
          nodes = process.nodes.filterNot(_ == node),
          edges = newEdges)
    }
  }

  private def getBranchRootToRemove(nodeEdges: NodeEdges): Option[NodeData] =
    nodeEdges.outgoing.collectFirst {
      case e@Edge(_, _, Some(FilterFalse)) =>
        process.nodes.find(_.id == e.to).get
    }

  private def getBypassEdge(edgesToBypass: NodeEdges): Option[Edge] =
    node match {
      case _: Sink => None

      case _  =>
        val incomingNode =
          process.nodes.find(_.id == edgesToBypass.incoming.head.from).get

        val incomingNodeOutEdge =
          getNodeEdges(incomingNode).outgoing.find(_.to == node.id).get

        val bypassTo = edgesToBypass.outgoing.head.to

        Some(Edge(
          from = incomingNode.id,
          to = bypassTo,
          edgeType = incomingNodeOutEdge.edgeType
        ))
    }

  private def getNodeEdges(node: NodeData): NodeEdges = NodeEdges(
    incoming = process.edges.filter(node.id == _.to),
    outgoing = process.edges.filter(node.id == _.from)
  )
}