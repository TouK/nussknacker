package pl.touk.nussknacker.engine.util

import cats.effect.unsafe.{IORuntime, IORuntimeConfig, Scheduler}
import com.typesafe.scalalogging.LazyLogging

import java.util.concurrent.Executor
import scala.concurrent.ExecutionContext

object SynchronousExecutionContextAndIORuntime extends LazyLogging {

  implicit val ctx: ExecutionContext = create()
  implicit val ioRuntime: IORuntime = iORuntimeFrom(ctx)

  def create(): ExecutionContext = ExecutionContext.fromExecutor(new Executor {
    def execute(task: Runnable): Unit = task.run()
  })

  private def iORuntimeFrom(ec: ExecutionContext): IORuntime = {
    val (scheduler, shutdown) = Scheduler.createDefaultScheduler()

    IORuntime(
      compute = ec,
      blocking = ec,
      scheduler = scheduler,
      shutdown = shutdown,
      config = IORuntimeConfig()
    )
  }
}
