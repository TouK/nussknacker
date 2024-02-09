package pl.touk.nussknacker.engine.management

import pl.touk.nussknacker.engine.BaseModelData
import pl.touk.nussknacker.engine.api.deployment.ProcessingTypeDeploymentService
import pl.touk.nussknacker.engine.management.FlinkStreamingRestManager.MainClassName
import sttp.client3.SttpBackend

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}

class FlinkStreamingRestManager(
    config: FlinkConfig,
    scenarioStateCacheTTL: Option[FiniteDuration],
    modelData: BaseModelData
)(
    implicit ec: ExecutionContext,
    backend: SttpBackend[Future, Any],
    deploymentService: ProcessingTypeDeploymentService
) extends FlinkRestManager(config, scenarioStateCacheTTL, modelData, mainClassName = MainClassName)

object FlinkStreamingRestManager {
  val MainClassName = "pl.touk.nussknacker.engine.process.runner.FlinkStreamingProcessMain"
}
