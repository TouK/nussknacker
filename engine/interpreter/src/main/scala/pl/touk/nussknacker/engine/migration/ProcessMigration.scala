package pl.touk.nussknacker.engine.migration

import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.canonicalgraph.canonicalnode._
import pl.touk.nussknacker.engine.graph.node.NodeData

/**
  * TODO: should this be in API??
  */
trait ProcessMigration {

   def description: String
  
   def failOnNewValidationError: Boolean

   def migrateProcess(canonicalProcess: CanonicalProcess): CanonicalProcess
}

object ProcessMigrations {

  def empty : ProcessMigrations = new ProcessMigrations {
    override def processMigrations: Map[Int, ProcessMigration] = Map()
  }

}

trait ProcessMigrations {

  def processMigrations: Map[Int, ProcessMigration]

  //we assume 0 is minimal version
  def version: Int = (processMigrations.keys.toSet + 0).max

}

trait FlatNodeMigration extends ProcessMigration {

  def migrateNode: PartialFunction[NodeData, NodeData]

  override def migrateProcess(canonicalProcess: CanonicalProcess): CanonicalProcess = {
    canonicalProcess.copy(nodes = migrateNodes(canonicalProcess.nodes))
  }

  //TODO: this is generic case of canonical process iteration, extract it...
  private def migrateNodes(nodes: List[CanonicalNode]) = nodes.map(migrateSingleNode)
  
  private def migrateSingleNode(node: CanonicalNode) : CanonicalNode = node match {
    case FlatNode(data) => FlatNode(migrateNode.applyOrElse(data, identity[NodeData]))
    case FilterNode(filter, nextFalse) => FilterNode(filter, nextFalse.map(migrateSingleNode))
    case SwitchNode(data, nexts, default) => SwitchNode(data, nexts.map(cas => cas.copy(nodes = migrateNodes(cas.nodes))),
      default.map(migrateSingleNode)
    )
    case SplitNode(data, nodes) => SplitNode(data, nodes.map(migrateNodes))
    case Subprocess(data, outputs) => Subprocess(data, outputs.mapValues(migrateNodes))
  }

}