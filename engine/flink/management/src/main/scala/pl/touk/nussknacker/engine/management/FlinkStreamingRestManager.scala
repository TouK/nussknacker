package pl.touk.nussknacker.engine.management

import pl.touk.nussknacker.engine.management.FlinkStreamingRestManager.MainClassName
import pl.touk.nussknacker.engine.management.rest.FlinkClient
import pl.touk.nussknacker.engine.{BaseModelData, DeploymentManagerDependencies}

class FlinkStreamingRestManager(
    client: FlinkClient,
    config: FlinkConfig,
    modelData: BaseModelData,
    dependencies: DeploymentManagerDependencies
) extends FlinkRestManager(client, config, modelData, dependencies, mainClassName = MainClassName)

object FlinkStreamingRestManager {
  val MainClassName = "pl.touk.nussknacker.engine.process.runner.FlinkStreamingProcessMain"
}
