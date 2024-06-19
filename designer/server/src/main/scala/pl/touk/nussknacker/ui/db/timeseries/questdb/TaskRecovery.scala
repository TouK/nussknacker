package pl.touk.nussknacker.ui.db.timeseries.questdb

import com.typesafe.scalalogging.LazyLogging
import io.questdb.cairo.{CairoEngine, CairoException, TableUtils}

import java.util.concurrent.atomic.AtomicReference
import scala.util.{Failure, Try}

private[questdb] class TaskRecovery(
    engine: AtomicReference[CairoEngine],
    recoverFromCriticalError: () => Try[Unit],
    tableName: String
) extends LazyLogging {

  def runWithRecover[T](dbAction: () => T): Try[T] = {
    // Table existence is checked to prevent data loss.
    // ApplyWalToTable does not throw exception on error so table existence need to be checked manually.
    if (tableExists(tableName)) {
      runDbAction(dbAction)
    } else {
      recoverWithRetry(dbAction)
    }
  }

  private def tableExists(tableName: String): Boolean =
    engine.get().getTableStatus(tableName) == TableUtils.TABLE_EXISTS

  private def runDbAction[T](dbAction: () => T): Try[T] = {
    Try(dbAction()).recoverWith {
      case ex: CairoException if ex.isCritical || ex.isTableDropped =>
        logger.warn("Statistic DB exception - trying to recover", ex)
        recoverWithRetry(dbAction)
      case ex =>
        logger.warn("DB exception", ex)
        Failure(ex)
    }
  }

  private def recoverWithRetry[T](dbAction: () => T): Try[T] = {
    recoverFromCriticalError().flatMap { _ =>
      Try(dbAction())
    }
  }

}
