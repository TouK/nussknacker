package pl.touk.nussknacker.engine.canonize

import pl.touk.nussknacker.engine.graph.node

// This trait and its 2 implementation encapsulate the logic of handling scenarios that do not end with sink
//  - older behavior is that error is returned for missing sinks
//  - new behavior, which we intend to become the default and only one in the future, is allowing missing sinks
sealed trait MissingSinkHandler {
  def handleMissingSink(previousNodeId: String): MaybeArtificial[Option[node.SubsequentNode]]
}

object MissingSinkHandler {

  object AllowMissingSinkHandler extends MissingSinkHandler {

    override def handleMissingSink(
        previousNodeId: String
    ): MaybeArtificial[Option[node.SubsequentNode]] =
      new MaybeArtificial(None, Nil)

  }

  object DoNotAllowMissingSinkHandler extends MissingSinkHandler {

    override def handleMissingSink(
        previousNodeId: String
    ): MaybeArtificial[Option[node.SubsequentNode]] =
      new MaybeArtificial(None, InvalidTailOfBranch(previousNodeId) :: Nil)

  }

}
