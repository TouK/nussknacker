package pl.touk.nussknacker.engine

import cats.effect.unsafe.IORuntime
import org.apache.pekko.actor.ActorSystem
import pl.touk.nussknacker.engine.api.deployment.ProcessingTypeDeployedScenariosProvider
import sttp.client3.SttpBackend

import scala.concurrent.{ExecutionContext, Future}

case class DeploymentManagerDependencies(
    deployedScenariosProvider: ProcessingTypeDeployedScenariosProvider,
    executionContext: ExecutionContext,
    ioRuntime: IORuntime,
    actorSystem: ActorSystem,
    sttpBackend: SttpBackend[Future, Any]
) {
  implicit def implicitExecutionContext: ExecutionContext    = executionContext
  implicit def implicitIORuntime: IORuntime                  = ioRuntime
  implicit def implicitActorSystem: ActorSystem              = actorSystem
  implicit def implicitSttpBackend: SttpBackend[Future, Any] = sttpBackend
}
