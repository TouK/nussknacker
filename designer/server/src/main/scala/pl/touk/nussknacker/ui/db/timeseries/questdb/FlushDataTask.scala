package pl.touk.nussknacker.ui.db.timeseries.questdb

import com.typesafe.scalalogging.LazyLogging
import io.questdb.cairo.CairoEngine
import io.questdb.cairo.wal.{ApplyWal2TableJob, WalWriter}

import java.util.concurrent.TimeUnit
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration.FiniteDuration

private[questdb] class FlushDataTask(
    private val engine: CairoEngine,
    private val walTableWriterPool: ThreadAwareObjectPool[WalWriter]
)(implicit ec: ExecutionContext)
    extends LazyLogging {
  private val workerCount       = 1
  private val applyWal2TableJob = new ApplyWal2TableJob(engine, workerCount, workerCount)

  def runUnsafe(aggregatedStatistics: Map[String, Long], currentTimeMicros: Long): Unit = {
    writeData(aggregatedStatistics, currentTimeMicros)
    applyWalToTable()
  }

  def close(): Unit = applyWal2TableJob.close()

  private def writeData(aggregatedStatistics: Map[String, Long], currentTimeMicros: Long) = {
    val walWriter = walTableWriterPool.get()
    aggregatedStatistics.foreach { case (key, value) =>
      val row = walWriter.newRow(currentTimeMicros)
      row.putStr(0, key)
      row.putLong(1, value)
      row.append()
    }
    walWriter.commit()
  }

  private def applyWalToTable(): Unit = {
    // This job is assigned to the WorkerPool created in Server mode (standalone db)
    // (io.questdb.ServerMain.setupWalApplyJob, io.questdb.mp.Worker.run),
    // so it's basically assigned thread to run this job.
    Await.result(
      Future {
        Range.inclusive(1, 3).takeWhile(idx => applyWal2TableJob.run(1) && idx <= 3)
      },
      FiniteDuration(10L, TimeUnit.SECONDS)
    )
  }

}
