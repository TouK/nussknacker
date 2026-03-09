package pl.touk.nussknacker.engine.splittedgraph

import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.graph.node.BranchEndDefinition

object end {

  sealed trait End {
    def nodeId: NodeId
  }

  case class NormalEnd(nodeId: NodeId) extends End

  case class DeadEnd(nodeId: NodeId) extends End

  // nodeId -> artifical
  case class BranchEnd(definition: BranchEndDefinition) extends End {
    override val nodeId: NodeId = NodeId(definition.artificialNodeId)
  }

}
