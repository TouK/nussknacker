package pl.touk.nussknacker.engine.util

import cats.effect.{IO, Resource}
import cats.effect.unsafe.{IORuntime, IORuntimeConfig}

import java.util.concurrent.Executors
import scala.concurrent.ExecutionContext

trait ExecutionContextWithIORuntime extends ExecutionContext {
  implicit def ioRuntime: IORuntime
}

class ExecutionContextWithIORuntimeAdapter private (executionContext: ExecutionContext)
    extends ExecutionContextWithIORuntime {

  private val cachedThreadPool = Executors.newCachedThreadPool()

  override implicit val ioRuntime: IORuntime = IORuntime(
    compute = executionContext,
    blocking = ExecutionContext.fromExecutor(cachedThreadPool),
    scheduler = IORuntime.global.scheduler,
    shutdown = () => (),
    config = IORuntimeConfig()
  )

  Runtime.getRuntime.addShutdownHook(new Thread() {
    override def run(): Unit = close()
  })

  override def execute(runnable: Runnable): Unit = executionContext.execute(runnable)

  override def reportFailure(cause: Throwable): Unit = executionContext.reportFailure(cause)

  @volatile private var closed = false

  def close(): Unit = {
    synchronized {
      if (!closed) {
        ioRuntime.shutdown()
        cachedThreadPool.shutdown()
        closed = true
      }
    }
  }

}

object ExecutionContextWithIORuntimeAdapter {

  def createFrom(executionContext: ExecutionContext): Resource[IO, ExecutionContextWithIORuntimeAdapter] = {
    Resource.make(IO.delay(new ExecutionContextWithIORuntimeAdapter(executionContext)))(ec => IO.delay(ec.close()))
  }

  def unsafeCreateFrom(executionContext: ExecutionContext): ExecutionContextWithIORuntimeAdapter = {
    new ExecutionContextWithIORuntimeAdapter(executionContext)
  }

}
