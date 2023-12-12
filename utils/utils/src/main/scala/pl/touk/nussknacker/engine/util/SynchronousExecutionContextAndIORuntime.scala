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

  private object DummyScheduler extends Scheduler {

    override def sleep(delay: FiniteDuration, task: Runnable): Runnable = {
      throw new IllegalStateException("I'm dummy scheduled. I should not be used by the production code.")
    }

    override def nowMillis(): Long = System.currentTimeMillis()

    override def nowMicros(): Long = {
      val now = Instant.now()
      now.getEpochSecond * 1000000 + now.getLong(ChronoField.MICRO_OF_SECOND)
    }

    override def monotonicNanos(): Long = System.nanoTime()
  }

}
