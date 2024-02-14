package pl.touk.nussknacker.engine.canonize

sealed trait ProcessUncanonizationError

object EmptyProcess extends ProcessUncanonizationError

sealed trait ProcessUncanonizationNodeError extends ProcessUncanonizationError {
  def nodeId: String
}

case class InvalidRootNode(nodeId: String) extends ProcessUncanonizationNodeError

case class InvalidTailOfBranch(nodeId: String) extends ProcessUncanonizationNodeError
