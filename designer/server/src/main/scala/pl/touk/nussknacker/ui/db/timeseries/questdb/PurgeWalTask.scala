package pl.touk.nussknacker.ui.db.timeseries.questdb

import io.questdb.cairo.CairoEngine
import io.questdb.cairo.wal.WalPurgeJob

class PurgeWalTask(private val engine: CairoEngine) {

  private val walPurgeJob = {
    val job = new WalPurgeJob(engine)
    engine.setWalPurgeJobRunLock(job.getRunLock)
    job
  }

  def runUnsafe(): Unit =
    walPurgeJob.run(2)

  def close(): Unit =
    walPurgeJob.close()
}
