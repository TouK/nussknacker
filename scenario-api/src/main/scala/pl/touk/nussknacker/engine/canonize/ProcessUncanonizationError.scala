package pl.touk.nussknacker.engine.canonize

import pl.touk.nussknacker.engine.api.NodeId

sealed trait ProcessUncanonizationError

object EmptyProcess extends ProcessUncanonizationError

sealed trait ProcessUncanonizationNodeError extends ProcessUncanonizationError {
  def nodeId: NodeId
}

case class InvalidRootNode(nodeId: NodeId) extends ProcessUncanonizationNodeError

case class InvalidTailOfBranch(nodeId: NodeId) extends ProcessUncanonizationNodeError
