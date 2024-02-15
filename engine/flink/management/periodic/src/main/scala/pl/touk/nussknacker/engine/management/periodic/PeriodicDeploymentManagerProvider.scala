package pl.touk.nussknacker.engine.management.periodic

import cats.data.ValidatedNel
import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api.component.ScenarioPropertyConfig
import pl.touk.nussknacker.engine.api.deployment.DeploymentManager
import pl.touk.nussknacker.engine.deployment.EngineSetupName
import pl.touk.nussknacker.engine.management.FlinkConfig
import pl.touk.nussknacker.engine.management.periodic.service._
import pl.touk.nussknacker.engine.util.config.ConfigEnrichments.RichConfig
import pl.touk.nussknacker.engine.{
  BaseModelData,
  DeploymentManagerDependencies,
  DeploymentManagerProvider,
  MetaDataInitializer
}

import scala.concurrent.duration.FiniteDuration

class PeriodicDeploymentManagerProvider(
    delegate: DeploymentManagerProvider,
    schedulePropertyExtractorFactory: SchedulePropertyExtractorFactory = _ => CronSchedulePropertyExtractor(),
    processConfigEnricherFactory: ProcessConfigEnricherFactory = ProcessConfigEnricherFactory.noOp,
    listenerFactory: PeriodicProcessListenerFactory = EmptyPeriodicProcessListenerFactory,
    additionalDeploymentDataProvider: AdditionalDeploymentDataProvider = DefaultAdditionalDeploymentDataProvider,
    customActionsProviderFactory: PeriodicCustomActionsProviderFactory = PeriodicCustomActionsProviderFactory.noOp
) extends DeploymentManagerProvider
    with LazyLogging {

  override def name: String = s"${delegate.name}Periodic"

  override def createDeploymentManager(
      modelData: BaseModelData,
      dependencies: DeploymentManagerDependencies,
      config: Config,
      scenarioStateCacheTTL: Option[FiniteDuration]
  ): ValidatedNel[String, DeploymentManager] = {
    logger.info("Creating periodic scenario manager")
    delegate.createDeploymentManager(modelData, dependencies, config, scenarioStateCacheTTL).map {
      delegateDeploymentManager =>
        import net.ceedubs.ficus.Ficus._
        import net.ceedubs.ficus.readers.ArbitraryTypeReader._
        val periodicBatchConfig = config.as[PeriodicBatchConfig]("deploymentManager")
        val flinkConfig         = config.rootAs[FlinkConfig]
        PeriodicDeploymentManager(
          delegate = delegateDeploymentManager,
          schedulePropertyExtractorFactory = schedulePropertyExtractorFactory,
          processConfigEnricherFactory = processConfigEnricherFactory,
          periodicBatchConfig = periodicBatchConfig,
          flinkConfig = flinkConfig,
          originalConfig = config,
          modelData = modelData,
          listenerFactory,
          additionalDeploymentDataProvider,
          customActionsProviderFactory,
          dependencies
        )
    }

  }

  override def metaDataInitializer(config: Config): MetaDataInitializer = delegate.metaDataInitializer(config)

  override def scenarioPropertiesConfig(config: Config): Map[String, ScenarioPropertyConfig] =
    delegate.scenarioPropertiesConfig(config)

  override def defaultEngineSetupName: EngineSetupName = delegate.defaultEngineSetupName

  override def engineSetupIdentity(config: Config): Any =
    delegate.engineSetupIdentity(config)

}
