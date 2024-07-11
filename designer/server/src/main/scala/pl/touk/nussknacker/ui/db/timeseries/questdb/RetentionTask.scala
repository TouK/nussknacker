package pl.touk.nussknacker.ui.db.timeseries.questdb

import cats.data.NonEmptyList
import com.typesafe.scalalogging.LazyLogging
import io.questdb.cairo.CairoEngine
import io.questdb.cairo.wal.WalPurgeJob
import io.questdb.griffin.SqlExecutionContext
import pl.touk.nussknacker.ui.db.timeseries.questdb.QuestDbExtensions.RecordCursorFactoryExtension
import pl.touk.nussknacker.ui.db.timeseries.questdb.RetentionTask.{
  buildDropPartitionsQuery,
  buildSelectAllPartitionsQuery
}

import java.time.Clock
import java.time.temporal.ChronoUnit
import scala.util.Try

private[questdb] class RetentionTask(
    private val engine: CairoEngine,
    private val tableName: String,
    private val sqlContextPool: ThreadAwareObjectPool[SqlExecutionContext],
    private val clock: Clock
) extends LazyLogging {

  private val selectAllPartitionsQuery = buildSelectAllPartitionsQuery(tableName)

  def runUnsafe(): Unit = {
    logger.info("Cleaning up old data")
    // TODO: remove it if automatic retention will be available: https://github.com/questdb/questdb/issues/4369
    val sqlContext    = sqlContextPool.get()
    val allPartitions = getPartitions(sqlContext)
    ensureOnePartitionExist(clock, allPartitions).foreach { partitionsToDrop =>
      val query = buildDropPartitionsQuery(tableName, partitionsToDrop)
      logger.info(s"Dropping old partitions: $query")
      engine.ddl(query, sqlContext)
      logger.info("Dropping old partitions succeed")
    }
  }

  private def getPartitions(sqlContext: SqlExecutionContext): List[(String, Long)] =
    engine.select(selectAllPartitionsQuery, sqlContext).fetch(sqlContext) { record =>
      record.getStrA(0).toString -> record.getTimestamp(1) / 1000L
    }

  // This check is needed because if the retention task would drop last partition, the db can't create new partition
  private def ensureOnePartitionExist(
      clock: Clock,
      allPartitions: List[(String, Long)]
  ): Option[NonEmptyList[String]] = {
    val todayInMillis = clock.instant().truncatedTo(ChronoUnit.DAYS).toEpochMilli
    allPartitions.span(_._2 < todayInMillis) match {
      case (oldPartitions @ _ :: _, _ :: _) => NonEmptyList.fromList(oldPartitions.map(_._1))
      case _                                => None
    }
  }

}

object RetentionTask {
  private def buildDropPartitionsQuery(tableName: String, partitions: NonEmptyList[String]): String =
    s"ALTER TABLE $tableName DROP PARTITION LIST ${partitions.map(p => s"'$p'").toList.mkString(",")}"
  private def buildSelectAllPartitionsQuery(tableName: String): String =
    s"select name, maxTimestamp from table_partitions('$tableName')"
}
