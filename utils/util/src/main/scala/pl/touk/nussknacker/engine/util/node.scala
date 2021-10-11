package pl.touk.nussknacker.engine.util

import pl.touk.nussknacker.engine.graph.node.{BranchEndData, BranchEndDefinition, NodeData, RealNodeData}

object node {

  def prefixNodeId[T <: NodeData](prefix: List[String], nodeData: T): T = {
    import pl.touk.nussknacker.engine.util.copySyntax._
    def prefixId(id: String): String = (prefix :+ id).mkString("-")
    //this casting is weird, but we want to have both exhaustiveness check and GADT behaviour with copy syntax...
    (nodeData.asInstanceOf[NodeData] match {
      case e: RealNodeData =>
        e.copy(id = prefixId(e.id))
      case BranchEndData(BranchEndDefinition(id, joinId)) =>
        BranchEndData(BranchEndDefinition(id, prefixId(joinId)))
    }).asInstanceOf[T]
  }

}
