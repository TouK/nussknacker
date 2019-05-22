package pl.touk.nussknacker.restmodel.displayedgraph

import pl.touk.nussknacker.engine.graph.node.{Disableable, NodeData}
import pl.touk.nussknacker.restmodel.displayedgraph.rewriter.{RemoveAndBypassNode, RemoveBranch}

object ProcessRewriter {

  def removeAndBypassNode(process: DisplayableProcess, node: NodeData): DisplayableProcess =
    new RemoveAndBypassNode(process, node).apply

  def removeBranch(process: DisplayableProcess, branchRoot: NodeData): DisplayableProcess =
    new RemoveBranch(process, branchRoot).apply

  def removeDisabledNodes(process: DisplayableProcess): DisplayableProcess =
    process.nodes.collectFirst { case d: Disableable => d } match {
      case Some(node) if node.isDisabled.getOrElse(false) =>
        removeDisabledNodes(removeAndBypassNode(process, node))
      case _ =>
        process
    }
}
