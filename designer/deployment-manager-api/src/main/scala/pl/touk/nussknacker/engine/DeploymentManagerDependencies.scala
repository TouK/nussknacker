package pl.touk.nussknacker.engine

import akka.actor.ActorSystem
import pl.touk.nussknacker.engine.api.deployment.ProcessingTypeDeploymentService
import sttp.client3.SttpBackend

import scala.concurrent.{ExecutionContext, Future}

case class DeploymentManagerDependencies(
    deploymentService: ProcessingTypeDeploymentService,
    executionContext: ExecutionContext,
    actorSystem: ActorSystem,
    sttpBackend: SttpBackend[Future, Any],
) {
  implicit def implicitExecutionContext: ExecutionContext    = executionContext
  implicit def implicitActorSystem: ActorSystem              = actorSystem
  implicit def implicitSttpBackend: SttpBackend[Future, Any] = sttpBackend
}
