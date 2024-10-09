package pl.touk.nussknacker.engine

import akka.actor.ActorSystem
import pl.touk.nussknacker.engine.api.deployment.{
  ProcessingTypeActionService,
  ProcessingTypeDeployedScenariosProvider,
  ScenarioActivityManager
}
import sttp.client3.SttpBackend

import scala.concurrent.{ExecutionContext, Future}

case class DeploymentManagerDependencies(
    deployedScenariosProvider: ProcessingTypeDeployedScenariosProvider,
    actionService: ProcessingTypeActionService,
    scenarioActivityManager: ScenarioActivityManager,
    executionContext: ExecutionContext,
    actorSystem: ActorSystem,
    sttpBackend: SttpBackend[Future, Any],
) {
  implicit def implicitExecutionContext: ExecutionContext    = executionContext
  implicit def implicitActorSystem: ActorSystem              = actorSystem
  implicit def implicitSttpBackend: SttpBackend[Future, Any] = sttpBackend
}
