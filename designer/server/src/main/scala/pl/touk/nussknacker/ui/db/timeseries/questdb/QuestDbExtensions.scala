package pl.touk.nussknacker.ui.db.timeseries.questdb

import com.typesafe.scalalogging.LazyLogging
import io.questdb.cairo.{CairoEngine, CairoException, TableUtils}
import io.questdb.cairo.sql.{Record, RecordCursorFactory}
import io.questdb.griffin.{SqlException, SqlExecutionContext}

import scala.collection.mutable.ArrayBuffer
import scala.util.{Failure, Try, Using}

private[questdb] trait QuestDbExtensions {

  implicit class RecordCursorFactoryExtension(factory: RecordCursorFactory) {

    def fetch[T](sqlContext: SqlExecutionContext)(mapper: Record => T): List[T] =
      Using.resource(factory.getCursor(sqlContext)) { recordCursor =>
        val buffer = ArrayBuffer.empty[T]
        val record = recordCursor.getRecord
        while (recordCursor.hasNext) {
          val entry = mapper(record)
          buffer.append(entry)
        }
        buffer.toList
      }

  }

  implicit class CairoEngineExtension(engine: CairoEngine) extends LazyLogging {
    def tableExists(tableName: String): Boolean =
      engine.getTableStatus(tableName) == TableUtils.TABLE_EXISTS

    def runWithExceptionHandling[T](
        recoverFromCriticalError: () => Try[Unit],
        tableName: String,
        dbAction: () => T
    ): T = {
      if (tableExists(tableName)) {
        runDbAction(recoverFromCriticalError, dbAction)
      } else {
        recoverWithRetry(recoverFromCriticalError, dbAction).get
      }
    }

    private def runDbAction[T](recoverFromCriticalError: () => Try[Unit], dbAction: () => T): T = {
      Try(dbAction()).recoverWith {
        case ex: CairoException if ex.isCritical || ex.isTableDropped =>
          logger.warn("Statistic DB exception - trying to recover", ex)
          recoverWithRetry(recoverFromCriticalError, dbAction)
        case ex: SqlException if ex.getMessage.contains("table does not exist") =>
          logger.warn("Statistic DB exception - trying to recover", ex)
          recoverWithRetry(recoverFromCriticalError, dbAction)
        case ex =>
          logger.warn("DB exception", ex)
          Failure(ex)
      }.get
    }

    private def recoverWithRetry[T](recoverFromCriticalError: () => Try[Unit], dbAction: () => T): Try[T] = {
      recoverFromCriticalError().flatMap { _ =>
        Try(dbAction())
      }
    }

  }

}

object QuestDbExtensions extends QuestDbExtensions
