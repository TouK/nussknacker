package pl.touk.nussknacker.engine.common.periodic

import cats.data.ValidatedNel
import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api.component.ScenarioPropertyConfig
import pl.touk.nussknacker.engine.api.definition.{MandatoryParameterValidator, StringParameterEditor}
import pl.touk.nussknacker.engine.api.deployment.DeploymentManager
import pl.touk.nussknacker.engine.api.deployment.periodic.PeriodicProcessesManagerProvider
import pl.touk.nussknacker.engine.common.periodic.cron.CronParameterValidator
import pl.touk.nussknacker.engine.common.periodic.service._
import pl.touk.nussknacker.engine.deployment.EngineSetupName
import pl.touk.nussknacker.engine.{
  BaseModelData,
  DeploymentManagerDependencies,
  DeploymentManagerProvider,
  MetaDataInitializer
}

import scala.concurrent.duration.FiniteDuration

abstract class PeriodicDeploymentManagerProvider(
    override val name: String,
    delegate: DeploymentManagerProvider,
) extends DeploymentManagerProvider
    with LazyLogging {

  private val cronConfig = CronSchedulePropertyExtractor.CronPropertyDefaultName -> ScenarioPropertyConfig(
    defaultValue = None,
    editor = Some(StringParameterEditor),
    validators = Some(List(MandatoryParameterValidator, CronParameterValidator.delegate)),
    label = Some("Schedule"),
    hintText = Some("Quartz cron syntax. You can specify multiple schedulers separated by '|'.")
  )

  protected def createPeriodicDeploymentHandler(
      modelData: BaseModelData,
      dependencies: DeploymentManagerDependencies,
      config: Config,
  ): PeriodicDeploymentHandler

  protected def createPeriodicProcessesManagerProvider(
      dependencies: DeploymentManagerDependencies,
      periodicBatchConfig: PeriodicBatchConfig,
  ): PeriodicProcessesManagerProvider = dependencies.periodicProcessesManagerProvider

  override def createDeploymentManager(
      modelData: BaseModelData,
      dependencies: DeploymentManagerDependencies,
      config: Config,
      scenarioStateCacheTTL: Option[FiniteDuration],
  ): ValidatedNel[String, DeploymentManager] = {
    logger.info("Creating periodic scenario manager")
    delegate.createDeploymentManager(modelData, dependencies, config, scenarioStateCacheTTL).map {
      delegateDeploymentManager =>
        import net.ceedubs.ficus.Ficus._
        import net.ceedubs.ficus.readers.ArbitraryTypeReader._
        val periodicBatchConfig              = config.as[PeriodicBatchConfig]("deploymentManager")
        val periodicProcessesManagerProvider = createPeriodicProcessesManagerProvider(dependencies, periodicBatchConfig)

        PeriodicDeploymentManager(
          delegate = delegateDeploymentManager,
          periodicDeploymentHandler = createPeriodicDeploymentHandler(modelData, dependencies, config),
          schedulePropertyExtractorFactory = _ => CronSchedulePropertyExtractor(),
          processConfigEnricherFactory = ProcessConfigEnricherFactory.noOp,
          periodicBatchConfig = periodicBatchConfig,
          originalConfig = config,
          EmptyPeriodicProcessListenerFactory,
          DefaultAdditionalDeploymentDataProvider,
          dependencies,
          periodicProcessesManagerProvider.provide(periodicBatchConfig.processingType)
        )
    }

  }

  override def metaDataInitializer(config: Config): MetaDataInitializer =
    delegate.metaDataInitializer(config)

  override def scenarioPropertiesConfig(config: Config): Map[String, ScenarioPropertyConfig] =
    Map(cronConfig) ++ delegate.scenarioPropertiesConfig(config)

  override def defaultEngineSetupName: EngineSetupName =
    delegate.defaultEngineSetupName

  override def engineSetupIdentity(config: Config): Any =
    delegate.engineSetupIdentity(config)

}