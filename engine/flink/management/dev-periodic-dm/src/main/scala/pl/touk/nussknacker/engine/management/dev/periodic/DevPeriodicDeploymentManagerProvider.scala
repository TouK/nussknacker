package pl.touk.nussknacker.engine.management.dev.periodic

import cats.data.ValidatedNel
import com.typesafe.config.Config
import pl.touk.nussknacker.engine.api.component.ScenarioPropertyConfig
import pl.touk.nussknacker.engine.api.definition.MandatoryParameterValidator
import pl.touk.nussknacker.engine.api.deployment.DeploymentManager
import pl.touk.nussknacker.engine.management.periodic.cron.CronParameterValidator
import pl.touk.nussknacker.engine.management.periodic.{
  CronSchedulePropertyExtractor,
  PeriodicDeploymentManagerProvider,
  WithRunNowPeriodicCustomActionsProviderFactory
}
import pl.touk.nussknacker.engine.management.{FlinkStreamingDeploymentManagerProvider, FlinkStreamingPropertiesConfig}
import pl.touk.nussknacker.engine.{
  BaseModelData,
  DeploymentManagerDependencies,
  DeploymentManagerProvider,
  MetaDataInitializer
}

import scala.concurrent.duration.FiniteDuration

class DevPeriodicDeploymentManagerProvider extends DeploymentManagerProvider {

  override def createDeploymentManager(
      modelData: BaseModelData,
      dependencies: DeploymentManagerDependencies,
      deploymentConfig: Config,
      scenarioStateCacheTTL: Option[FiniteDuration]
  ): ValidatedNel[String, DeploymentManager] = {
    // TODO: make possible to use PeriodicDeploymentManagerProvider with non-flink DMs like embedded or lite-k8s
    new PeriodicDeploymentManagerProvider(
      delegate = new FlinkStreamingDeploymentManagerProvider(),
      customActionsProviderFactory = new WithRunNowPeriodicCustomActionsProviderFactory
    )
      .createDeploymentManager(modelData, dependencies, deploymentConfig, scenarioStateCacheTTL)
  }

  override def metaDataInitializer(config: Config): MetaDataInitializer =
    FlinkStreamingPropertiesConfig.metaDataInitializer

  // TODO: move it to PeriodicDeploymentManagerProvider with ability to override
  override def scenarioPropertiesConfig(config: Config): Map[String, ScenarioPropertyConfig] = Map(
    CronSchedulePropertyExtractor.CronPropertyDefaultName -> ScenarioPropertyConfig(
      defaultValue = None, // TODO: Maybe once a day at 0:00 ?
      editor = None,
      validators = Some(List(MandatoryParameterValidator, CronParameterValidator.delegate)),
      label = None,
      hintText = None
    )
  ) ++ FlinkStreamingPropertiesConfig.properties

  override def name: String = "dev-periodic"

}
