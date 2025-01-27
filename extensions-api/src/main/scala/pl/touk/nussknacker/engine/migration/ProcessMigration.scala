package pl.touk.nussknacker.engine.migration

import pl.touk.nussknacker.engine.api.MetaData
import pl.touk.nussknacker.engine.canonicalgraph.{CanonicalProcess, ProcessNodesRewriter}
import pl.touk.nussknacker.engine.graph.node.NodeData

import scala.reflect.ClassTag

trait ProcessMigration {

  def description: String

  // category is used in some of external migrations
  def migrateProcess(canonicalProcess: CanonicalProcess, category: String): CanonicalProcess

}

/**
 * It migrates data of each node in process without changing the structure of process graph.
 */
trait NodeMigration extends ProcessMigration {

  def migrateNode(metaData: MetaData): PartialFunction[NodeData, NodeData]

  override def migrateProcess(canonicalProcess: CanonicalProcess, category: String): CanonicalProcess = {
    val rewriter = new ProcessNodesRewriter {
      override protected def rewriteNode[T <: NodeData: ClassTag](data: T)(implicit metaData: MetaData): Option[T] =
        migrateNode(metaData).lift(data).map(_.asInstanceOf[T])
    }
    rewriter.rewriteProcess(canonicalProcess)
  }

}
