package pl.touk.nussknacker.engine.splittedgraph

object end {

  sealed trait End {
    def nodeId: String
  }

  case class NormalEnd(nodeId: String) extends End

  case class DeadEnd(nodeId: String) extends End

}
