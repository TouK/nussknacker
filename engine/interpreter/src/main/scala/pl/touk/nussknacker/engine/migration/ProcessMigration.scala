package pl.touk.nussknacker.engine.migration

import pl.touk.nussknacker.engine.api.MetaData
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.canonicalgraph.canonicalnode._
import pl.touk.nussknacker.engine.graph.node.{NodeData, SubprocessInput}

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

  def migrateNode(metaData: MetaData): PartialFunction[NodeData, NodeData]

  override def migrateProcess(canonicalProcess: CanonicalProcess): CanonicalProcess = {
    canonicalProcess.copy(nodes = migrateNodes(canonicalProcess.nodes, canonicalProcess.metaData))
  }

  //TODO: this is generic case of canonical process iteration, extract it...
  private def migrateNodes(nodes: List[CanonicalNode], metaData: MetaData) = nodes.map(migrateSingleNode(_, metaData))

  private def migrateSingleNode(node: CanonicalNode, metaData: MetaData): CanonicalNode = node match {
    case FlatNode(data) =>
      FlatNode(migrateNode(metaData).applyOrElse(data, identity[NodeData]))
    case FilterNode(filter, nextFalse) =>
      FilterNode(filter, nextFalse.map(migrateSingleNode(_, metaData)))
    case SwitchNode(data, nexts, default) =>
      SwitchNode(data, nexts.map(cas => cas.copy(nodes = migrateNodes(cas.nodes, metaData))),
      default.map(migrateSingleNode(_, metaData))
    )
    case SplitNode(data, nodes) =>
      SplitNode(data, nodes.map(migrateNodes(_, metaData)))
    case Subprocess(data, outputs) =>
      val newData = migrateNode(metaData).applyOrElse(data, identity[SubprocessInput]).asInstanceOf[SubprocessInput]
      Subprocess(newData, outputs.mapValues(migrateNodes(_, metaData)))
  }

}