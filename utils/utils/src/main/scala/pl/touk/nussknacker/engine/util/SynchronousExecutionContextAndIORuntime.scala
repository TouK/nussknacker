package pl.touk.nussknacker.engine.util

import cats.effect.unsafe.{IORuntime, IORuntimeConfig, Scheduler}
import com.typesafe.scalalogging.LazyLogging

import java.time.Instant
import java.time.temporal.ChronoField
import java.util.concurrent.Executor
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.FiniteDuration

object SynchronousExecutionContextAndIORuntime extends LazyLogging {

  implicit val syncEc: ExecutionContext = createSyncExecutionContext()
  implicit val syncIoRuntime: IORuntime = createSyncIORuntime()

  private def createSyncExecutionContext(): ExecutionContext = ExecutionContext.fromExecutor(new Executor {
    def execute(task: Runnable): Unit = task.run()
  })

  private def createSyncIORuntime(): IORuntime = {
    IORuntime(
      compute = syncEc,
      blocking = syncEc,
      scheduler = DummyScheduler,
      shutdown = () => {},
      config = IORuntimeConfig()
    )
  }

  // note: we provide a dummy implementation of scheduler, because IORuntime requires some. We don't want to use real
  //       (e.g. ScheduledExecutorService-based) implementation, because we will need to close it. Currently, closing
  //       shared resources used by Flink's Task Manager is tricky and doesn't work well. Moreover, currently we don't
  //       use a scheduler from the `syncIoRuntime` anywhere. With the dummy implementation of Scheduler we don't have
  //       to bother with closing it.
  private object DummyScheduler extends Scheduler {

    override def sleep(delay: FiniteDuration, task: Runnable): Runnable = {
      throw new IllegalStateException("I'm dummy scheduler. I should not be used by the production code.")
    }

    override def nowMillis(): Long = System.currentTimeMillis()

    override def nowMicros(): Long = {
      val now = Instant.now()
      now.getEpochSecond * 1000000 + now.getLong(ChronoField.MICRO_OF_SECOND)
    }

    override def monotonicNanos(): Long = System.nanoTime()
  }

}
