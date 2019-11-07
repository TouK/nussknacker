package pl.touk.nussknacker.engine.migration

import pl.touk.nussknacker.engine.api.MetaData
import pl.touk.nussknacker.engine.canonicalgraph.{CanonicalProcess, ProcessRewriter}
import pl.touk.nussknacker.engine.graph.exceptionhandler
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

  def migrateNode(metaData: MetaData): PartialFunction[NodeData, NodeData]

  override def migrateProcess(canonicalProcess: CanonicalProcess): CanonicalProcess = {
    val rewriter = new ProcessRewriter {
      override protected def rewriteExpressionHandler(implicit metaData: MetaData):
      PartialFunction[exceptionhandler.ExceptionHandlerRef, exceptionhandler.ExceptionHandlerRef] = PartialFunction.empty

      override protected def rewriteNode(implicit metaData: MetaData): PartialFunction[NodeData, NodeData] = migrateNode(metaData)
    }
    rewriter.rewriteProcess(canonicalProcess)
  }

}