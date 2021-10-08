package pl.touk.nussknacker.engine.splittedgraph

import pl.touk.nussknacker.engine.graph.node.BranchEndDefinition

object end {

  sealed trait End {
    def nodeId: String
  }

  case class NormalEnd(nodeId: String) extends End

  case class DeadEnd(nodeId: String) extends End

  //nodeId -> artifical
  case class BranchEnd(definition: BranchEndDefinition) extends End {
    override val nodeId: String = definition.artificialNodeId
  }


}
