package pl.touk.nussknacker.engine.migration

import pl.touk.nussknacker.engine.api.MetaData
import pl.touk.nussknacker.engine.canonicalgraph.{CanonicalProcess, ProcessNodesRewriter}
import pl.touk.nussknacker.engine.graph.exceptionhandler
import pl.touk.nussknacker.engine.graph.exceptionhandler.ExceptionHandlerRef
import pl.touk.nussknacker.engine.graph.node.NodeData

import scala.reflect.ClassTag

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

trait ProcessMigrations extends Serializable {

  def processMigrations: Map[Int, ProcessMigration]

  //we assume 0 is minimal version
  def version: Int = (processMigrations.keys.toSet + 0).max

}

/**
  * It migrates data of each node in process without changing the structure of process graph.
  */
trait NodeMigration extends ProcessMigration {

  def migrateNode(metaData: MetaData): PartialFunction[NodeData, NodeData]

  override def migrateProcess(canonicalProcess: CanonicalProcess): CanonicalProcess = {
    val rewriter = new ProcessNodesRewriter {
      override protected def rewriteExceptionHandler(exceptionHandlerRef: ExceptionHandlerRef)(implicit metaData: MetaData): Option[ExceptionHandlerRef] =
        None

      override protected def rewriteNode[T <: NodeData: ClassTag](data: T)(implicit metaData: MetaData): Option[T] =
        migrateNode(metaData).lift(data).map(_.asInstanceOf[T])
    }
    rewriter.rewriteProcess(canonicalProcess)
  }

}