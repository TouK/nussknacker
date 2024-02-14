package pl.touk.nussknacker.engine.management

import pl.touk.nussknacker.engine.management.FlinkStreamingRestManager.MainClassName
import pl.touk.nussknacker.engine.{BaseModelData, DeploymentManagerDependencies}

import scala.concurrent.duration.FiniteDuration

class FlinkStreamingRestManager(
    config: FlinkConfig,
    scenarioStateCacheTTL: Option[FiniteDuration],
    modelData: BaseModelData,
    dependencies: DeploymentManagerDependencies
) extends FlinkRestManager(config, scenarioStateCacheTTL, modelData, dependencies, mainClassName = MainClassName)

object FlinkStreamingRestManager {
  val MainClassName = "pl.touk.nussknacker.engine.process.runner.FlinkStreamingProcessMain"
}
