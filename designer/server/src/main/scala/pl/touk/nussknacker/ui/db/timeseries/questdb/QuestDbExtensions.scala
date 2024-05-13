package pl.touk.nussknacker.ui.db.timeseries.questdb

import io.questdb.cairo.{CairoEngine, TableUtils}
import io.questdb.cairo.sql.{Record, RecordCursorFactory}
import io.questdb.griffin.SqlExecutionContext

import scala.collection.mutable.ArrayBuffer
import scala.util.Using

trait QuestDbExtensions {

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

  implicit class CairoEngineExtension(engine: CairoEngine) {
    def tableExists(tableName: String): Boolean =
      engine.getTableStatus(tableName) == TableUtils.TABLE_EXISTS
  }

}

object QuestDbExtensions extends QuestDbExtensions
