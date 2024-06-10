package pl.touk.nussknacker.ui.db.timeseries.questdb

import com.typesafe.scalalogging.LazyLogging
import io.questdb.cairo.CairoEngine
import io.questdb.cairo.wal.ApplyWal2TableJob
import pl.touk.nussknacker.ui.db.timeseries.questdb.QuestDbExtensions.CairoEngineExtension

import scala.util.Try

private[questdb] class FlushDataTask(private val engine: CairoEngine, private val tableName: String)
    extends LazyLogging {
  private val workerCount       = 1
  private val applyWal2TableJob = new ApplyWal2TableJob(engine, workerCount, workerCount)

  def run(): Unit = {
    logger.info("Flushing data to disk")
    if (!engine.tableExists(tableName)) {
      logger.warn(s"Table does not exist - flushing data to disk skipped")
      return
    }
    // This job is assigned to the WorkerPool created in Server mode (standalone db)
    // (io.questdb.ServerMain.setupWalApplyJob, io.questdb.mp.Worker.run),
    // so it's basically assigned thread to run this job.
    Try {
      Range.inclusive(1, 3).takeWhile(idx => applyWal2TableJob.run(1) && idx <= 3)
    }.recover { case ex: Exception =>
      logger.warn("Exception thrown while applying a data to the disk.", ex)
    }
  }

  def close(): Unit = applyWal2TableJob.close()
}
