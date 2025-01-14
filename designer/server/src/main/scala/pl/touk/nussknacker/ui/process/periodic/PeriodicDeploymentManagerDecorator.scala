package pl.touk.nussknacker.ui.process.periodic

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api.component.ScenarioPropertyConfig
import pl.touk.nussknacker.engine.api.definition.{MandatoryParameterValidator, StringParameterEditor}
import pl.touk.nussknacker.engine.api.deployment.periodic.services.{
  EmptyPeriodicProcessListenerFactory,
  PeriodicSchedulePropertyExtractorFactory,
  ProcessConfigEnricherFactory
}
import pl.touk.nussknacker.engine.api.deployment.{DeploymentManager, PeriodicExecutionSupported}
import pl.touk.nussknacker.engine.{DeploymentManagerDependencies, ModelData}
import pl.touk.nussknacker.ui.process.periodic.cron.{CronParameterValidator, CronSchedulePropertyExtractor}

object PeriodicDeploymentManagerDecorator extends LazyLogging {

  def decorate(
      underlying: DeploymentManager,
      periodicExecutionSupported: PeriodicExecutionSupported,
      periodicProcessesManagerProvider: PeriodicProcessesManagerProvider,
      modelData: ModelData,
      deploymentConfig: Config,
      dependencies: DeploymentManagerDependencies,
  ): DeploymentManager = {
    logger.info("Decorating DM with periodic functionality")
    import net.ceedubs.ficus.Ficus._
    import net.ceedubs.ficus.readers.ArbitraryTypeReader._
    val periodicBatchConfig = deploymentConfig.as[PeriodicBatchConfig]("deploymentManager")

    val schedulePropertyExtractorFactory: PeriodicSchedulePropertyExtractorFactory =
      periodicExecutionSupported.customSchedulePropertyExtractorFactory
        .getOrElse(_ => CronSchedulePropertyExtractor())

    val processConfigEnricherFactory =
      periodicExecutionSupported.customProcessConfigEnricherFactory
        .getOrElse(ProcessConfigEnricherFactory.noOp)

    val periodicProcessListenerFactory =
      periodicExecutionSupported.customPeriodicProcessListenerFactory
        .getOrElse(EmptyPeriodicProcessListenerFactory)

    val additionalDeploymentDataProvider =
      periodicExecutionSupported.customAdditionalDeploymentDataProvider
        .getOrElse(DefaultAdditionalDeploymentDataProvider)

    PeriodicDeploymentManager(
      delegate = underlying,
      dependencies = dependencies,
      periodicProcessesManager = periodicProcessesManagerProvider.provide(
        periodicBatchConfig.processingType,
        periodicBatchConfig.db
      ),
      engineHandler = periodicExecutionSupported.engineHandler(modelData, dependencies, deploymentConfig),
      schedulePropertyExtractorFactory = schedulePropertyExtractorFactory,
      processConfigEnricherFactory = processConfigEnricherFactory,
      listenerFactory = periodicProcessListenerFactory,
      periodicBatchConfig = periodicBatchConfig,
      originalConfig = deploymentConfig,
      additionalDeploymentDataProvider = additionalDeploymentDataProvider,
    )
  }

  def additionalScenarioProperties: Map[String, ScenarioPropertyConfig] = Map(cronConfig)

  private val cronConfig = CronSchedulePropertyExtractor.CronPropertyDefaultName -> ScenarioPropertyConfig(
    defaultValue = None,
    editor = Some(StringParameterEditor),
    validators = Some(List(MandatoryParameterValidator, CronParameterValidator)),
    label = Some("Schedule"),
    hintText = Some("Quartz cron syntax. You can specify multiple schedulers separated by '|'.")
  )

}
