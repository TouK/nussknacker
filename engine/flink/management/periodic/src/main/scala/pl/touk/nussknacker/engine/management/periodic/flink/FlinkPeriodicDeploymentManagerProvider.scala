package pl.touk.nussknacker.engine.management.periodic.flink

import com.typesafe.config.Config
import pl.touk.nussknacker.engine.api.deployment.periodic.PeriodicProcessesManagerProvider
import pl.touk.nussknacker.engine.common.periodic.{
  PeriodicBatchConfig,
  PeriodicDeploymentHandler,
  PeriodicDeploymentManagerProvider
}
import pl.touk.nussknacker.engine.management.periodic.flink.db.{
  LegacyDbInitializer,
  LegacyRepositoryBasedPeriodicProcessesManager,
  SlickLegacyPeriodicProcessesRepository
}
import pl.touk.nussknacker.engine.management.{FlinkConfig, FlinkStreamingDeploymentManagerProvider}
import pl.touk.nussknacker.engine.util.config.ConfigEnrichments.RichConfig
import pl.touk.nussknacker.engine.{BaseModelData, DeploymentManagerDependencies}
import slick.jdbc
import slick.jdbc.JdbcProfile

import java.time.Clock

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

  override protected def createPeriodicProcessesManagerProvider(
      dependencies: DeploymentManagerDependencies,
      periodicBatchConfig: PeriodicBatchConfig,
  ): PeriodicProcessesManagerProvider = {
    import dependencies._
    periodicBatchConfig.db match {
      case None =>
        dependencies.periodicProcessesManagerProvider
      case Some(customDbConfig) =>
        val clock                                                      = Clock.systemDefaultZone()
        val (db: jdbc.JdbcBackend.DatabaseDef, dbProfile: JdbcProfile) = LegacyDbInitializer.init(customDbConfig)
        val repository = new SlickLegacyPeriodicProcessesRepository(db, dbProfile, clock)
        (processingType: String) => new LegacyRepositoryBasedPeriodicProcessesManager(processingType, repository)
    }
  }

}
