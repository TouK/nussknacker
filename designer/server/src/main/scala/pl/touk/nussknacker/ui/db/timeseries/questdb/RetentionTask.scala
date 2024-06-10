package pl.touk.nussknacker.ui.db.timeseries.questdb

import com.typesafe.scalalogging.LazyLogging
import io.questdb.cairo.CairoEngine
import io.questdb.cairo.wal.WalPurgeJob
import io.questdb.griffin.SqlExecutionContext
import pl.touk.nussknacker.ui.db.timeseries.questdb.QuestDbExtensions.RecordCursorFactoryExtension

import java.time.Clock
import java.time.temporal.ChronoUnit

private[questdb] class RetentionTask(private val engine: CairoEngine, private val tableName: String)
    extends LazyLogging {

  private val walPurgeJob = {
    val job = new WalPurgeJob(engine)
    engine.setWalPurgeJobRunLock(job.getRunLock)
    job
  }

  private val dropOldPartitionsQuery   = RetentionTask.dropOldPartitionsQuery(tableName)
  private val selectAllPartitionsQuery = RetentionTask.selectAllPartitionsQuery(tableName)

  // todo compact data?
  def run(sqlContext: SqlExecutionContext, clock: Clock): Unit = {
    logger.info("Cleaning up old data")
    // TODO: remove it if automatic retention will be available: https://github.com/questdb/questdb/issues/4369
    if (existsPartitionForDrop(sqlContext, clock)) {
      logger.info("Dropping old partitions")
      engine.ddl(dropOldPartitionsQuery, sqlContext)
    }
    walPurgeJob.run(2)
  }

  def close(): Unit =
    walPurgeJob.close()

  // This check is needed because if the retention task would drop last partition, the db can't create new partition
  private def existsPartitionForDrop(sqlContext: SqlExecutionContext, clock: Clock): Boolean = {
    val allPartitions = engine.select(selectAllPartitionsQuery, sqlContext).fetch(sqlContext) { record =>
      record.getStrA(0).toString -> record.getTimestamp(1) / 1000L
    }
    val (oldPartitions, presentPartitions) = allPartitions.span(
      _._2 < clock.instant().truncatedTo(ChronoUnit.DAYS).toEpochMilli
    )
    oldPartitions.nonEmpty && presentPartitions.nonEmpty
  }

}

object RetentionTask {
  private def dropOldPartitionsQuery(tableName: String): String =
    s"ALTER TABLE $tableName DROP PARTITION WHERE ts < timestamp_floor('d', now())"
  private def selectAllPartitionsQuery(tableName: String): String =
    s"select name, maxTimestamp from table_partitions('$tableName')"
}
