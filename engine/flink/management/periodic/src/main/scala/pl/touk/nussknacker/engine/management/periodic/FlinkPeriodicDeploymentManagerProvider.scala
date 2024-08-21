package pl.touk.nussknacker.engine.management.periodic

import cats.data.ValidatedNel
import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api.component.ScenarioPropertyConfig
import pl.touk.nussknacker.engine.api.definition.{MandatoryParameterValidator, StringParameterEditor}
import pl.touk.nussknacker.engine.api.deployment.DeploymentManager
import pl.touk.nussknacker.engine.api.properties.ScenarioProperties
import pl.touk.nussknacker.engine.deployment.EngineSetupName
import pl.touk.nussknacker.engine.management.periodic.cron.CronParameterValidator
import pl.touk.nussknacker.engine.management.{FlinkConfig, FlinkStreamingDeploymentManagerProvider}
import pl.touk.nussknacker.engine.management.periodic.service._
import pl.touk.nussknacker.engine.util.config.ConfigEnrichments.RichConfig
import pl.touk.nussknacker.engine.{
  BaseModelData,
  DeploymentManagerDependencies,
  DeploymentManagerProvider,
  MetaDataInitializer
}

import scala.concurrent.duration.FiniteDuration

class FlinkPeriodicDeploymentManagerProvider extends DeploymentManagerProvider with LazyLogging {

  private val delegate = new FlinkStreamingDeploymentManagerProvider()

  private val cronConfig = CronSchedulePropertyExtractor.CronPropertyDefaultName -> ScenarioPropertyConfig(
    defaultValue = None,
    editor = Some(StringParameterEditor),
    validators = Some(List(MandatoryParameterValidator, CronParameterValidator.delegate)),
    label = Some("Schedule"),
    hintText = Some("Quartz cron syntax. You can specify multiple schedulers separated by '|'.")
  )

  override def name: String = "flinkPeriodic"

  override def createDeploymentManager(
      modelData: BaseModelData,
      dependencies: DeploymentManagerDependencies,
      config: Config,
      scenarioStateCacheTTL: Option[FiniteDuration]
  ): ValidatedNel[String, DeploymentManager] = {
    logger.info("Creating FlinkPeriodic scenario manager")
    delegate.createDeploymentManager(modelData, dependencies, config, scenarioStateCacheTTL).map {
      delegateDeploymentManager =>
        import net.ceedubs.ficus.Ficus._
        import net.ceedubs.ficus.readers.ArbitraryTypeReader._
        val periodicBatchConfig = config.as[PeriodicBatchConfig]("deploymentManager")
        val flinkConfig         = config.rootAs[FlinkConfig]

        PeriodicDeploymentManager(
          delegate = delegateDeploymentManager,
          schedulePropertyExtractorFactory = _ => CronSchedulePropertyExtractor(),
          processConfigEnricherFactory = ProcessConfigEnricherFactory.noOp,
          periodicBatchConfig = periodicBatchConfig,
          flinkConfig = flinkConfig,
          originalConfig = config,
          modelData = modelData,
          EmptyPeriodicProcessListenerFactory,
          DefaultAdditionalDeploymentDataProvider,
          new WithRunNowPeriodicCustomActionsProviderFactory,
          dependencies
        )
    }

  }

  override def metaDataInitializer(config: Config): MetaDataInitializer =
    delegate.metaDataInitializer(config)

  override def scenarioPropertiesConfig(config: Config): ScenarioProperties = {
    val props = delegate.scenarioPropertiesConfig(config)
    props.copy(props.propertiesConfig ++ Map(cronConfig))
  }

  override def defaultEngineSetupName: EngineSetupName =
    delegate.defaultEngineSetupName

  override def engineSetupIdentity(config: Config): Any =
    delegate.engineSetupIdentity(config)

}
