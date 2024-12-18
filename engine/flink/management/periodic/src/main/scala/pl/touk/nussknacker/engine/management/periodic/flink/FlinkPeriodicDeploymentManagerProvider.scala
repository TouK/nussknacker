package pl.touk.nussknacker.engine.management.periodic.flink

import com.typesafe.config.Config
import pl.touk.nussknacker.engine.common.periodic.{
  PeriodicBatchConfig,
  PeriodicDeploymentHandler,
  PeriodicDeploymentManagerProvider
}
import pl.touk.nussknacker.engine.management.{FlinkConfig, FlinkStreamingDeploymentManagerProvider}
import pl.touk.nussknacker.engine.util.config.ConfigEnrichments.RichConfig
import pl.touk.nussknacker.engine.{BaseModelData, DeploymentManagerDependencies}

class FlinkPeriodicDeploymentManagerProvider
    extends PeriodicDeploymentManagerProvider(
      name = "flinkPeriodic",
      delegate = new FlinkStreamingDeploymentManagerProvider(),
    ) {

  override protected def createPeriodicDeploymentHandler(
      modelData: BaseModelData,
      dependencies: DeploymentManagerDependencies,
      config: Config
  ): PeriodicDeploymentHandler = {
    import dependencies._
    import net.ceedubs.ficus.Ficus._
    import net.ceedubs.ficus.readers.ArbitraryTypeReader._
    val periodicBatchConfig = config.as[PeriodicBatchConfig]("deploymentManager")
    val flinkConfig         = config.rootAs[FlinkConfig]

    FlinkPeriodicDeploymentHandler(
      flinkConfig = flinkConfig,
      jarsDir = periodicBatchConfig.jarsDir,
      modelData = modelData,
    )
  }

}
