package pl.touk.nussknacker.engine.management.savepoint

import scala.concurrent.Future

trait FlinkSavepointLocator {
  import FlinkSavepointLocator.Savepoint

  def locateSavepoint(path: String): Future[Savepoint]
}

object FlinkSavepointLocator {

  sealed trait Savepoint extends AutoCloseable {
    val path: String
  }

  case class LocalSavepoint(override val path: String) extends Savepoint {
    override def close(): Unit = {}
  }

}
