package pl.touk.nussknacker.defaultmodel.migrations

import cats.data.NonEmptyList
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.canonicalgraph.canonicalnode._
import pl.touk.nussknacker.engine.graph.node.CustomNode
import pl.touk.nussknacker.engine.migration.ProcessMigration

/**
  * Rewires a custom node from the released unnamed-edge form to the named-outputs form:
  * `FlatNode(custom) :: tail` becomes `CustomNodeWithOutputs(custom, Output(mainOutputName, tail))`.
  * A node with no continuation keeps the flat ending shape. Idempotent: an already migrated node is a
  * `CustomNodeWithOutputs`, which is only recursed into, never rewrapped.
  */
final case class CustomNodeOutputsMigration(nodeType: String, mainOutputName: String) extends ProcessMigration {

  override def description: String =
    s"Connect '$nodeType' through its named main output '$mainOutputName' instead of an unnamed edge"

  override def migrateProcess(canonicalProcess: CanonicalProcess, category: String): CanonicalProcess =
    canonicalProcess.mapAllNodes(migrateBranch)

  private def migrateBranch(nodes: List[CanonicalNode]): List[CanonicalNode] =
    nodes match {
      case Nil => Nil
      case FlatNode(custom: CustomNode) :: tail if custom.nodeType == nodeType && tail.nonEmpty =>
        CustomNodeWithOutputs(custom, NonEmptyList.of(Output(mainOutputName, migrateBranch(tail)))) :: Nil
      case node :: tail => mapBranches(node)(migrateBranch) :: migrateBranch(tail)
    }

}
