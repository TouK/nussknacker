package pl.touk.nussknacker.ui.util

import akka.actor.ActorSystem
import cats.effect.unsafe.{IORuntime, IORuntimeConfig}

import java.util.concurrent.Executors
import scala.concurrent.ExecutionContext

trait ExecutionContextWithIORuntime extends ExecutionContext {
  implicit def ioRuntime: IORuntime
}

class ActorSystemBasedExecutionContextWithIORuntime private (actorSystem: ActorSystem)
    extends ExecutionContextWithIORuntime {

  private val cachedThreadPool = Executors.newCachedThreadPool()

  override implicit val ioRuntime: IORuntime = IORuntime(
    compute = actorSystem.dispatcher,
    blocking = ExecutionContext.fromExecutor(cachedThreadPool),
    scheduler = IORuntime.global.scheduler,
    shutdown = () => (),
    config = IORuntimeConfig()
  )

  actorSystem.registerOnTermination {
    ioRuntime.shutdown()
    cachedThreadPool.shutdown()
  }

  override def execute(runnable: Runnable): Unit = actorSystem.dispatcher.execute(runnable)

  override def reportFailure(cause: Throwable): Unit = actorSystem.dispatcher.reportFailure(cause)
}

object ActorSystemBasedExecutionContextWithIORuntime {
  def createFrom(actorSystem: ActorSystem): ActorSystemBasedExecutionContextWithIORuntime =
    new ActorSystemBasedExecutionContextWithIORuntime(actorSystem)
}
