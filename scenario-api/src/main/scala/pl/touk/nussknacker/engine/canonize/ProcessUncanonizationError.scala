package pl.touk.nussknacker.engine.canonize

sealed trait ProcessUncanonizationError

object EmptyProcess extends ProcessUncanonizationError

case class InvalidRootNode(nodeId: String) extends ProcessUncanonizationError

case class InvalidTailOfBranch(nodeId: String) extends ProcessUncanonizationError
