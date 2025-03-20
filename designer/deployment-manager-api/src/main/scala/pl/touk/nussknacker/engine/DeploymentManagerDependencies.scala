package pl.touk.nussknacker.engine

import cats.effect.unsafe.IORuntime
import org.apache.pekko.actor.ActorSystem
import sttp.client3.SttpBackend

import scala.concurrent.{ExecutionContext, Future}

final class DeploymentManagerDependencies(
    val executionContext: ExecutionContext,
    val ioRuntime: IORuntime,
    val actorSystem: ActorSystem,
    val sttpBackend: SttpBackend[Future, Any]
) {
  implicit def implicitExecutionContext: ExecutionContext    = executionContext
  implicit def implicitIORuntime: IORuntime                  = ioRuntime
  implicit def implicitActorSystem: ActorSystem              = actorSystem
  implicit def implicitSttpBackend: SttpBackend[Future, Any] = sttpBackend
}
