package pl.touk.nussknacker.ui.process.periodic

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.DeploymentManagerDependencies
import pl.touk.nussknacker.engine.api.component.ScenarioPropertyConfig
import pl.touk.nussknacker.engine.api.definition.{MandatoryParameterValidator, StringParameterEditor}
import pl.touk.nussknacker.engine.api.deployment.{DeploymentManager, SchedulingSupported}
import pl.touk.nussknacker.engine.api.deployment.scheduler.services.{
  EmptyScheduledProcessListenerFactory,
  ProcessConfigEnricherFactory,
  SchedulePropertyExtractorFactory
}
import pl.touk.nussknacker.ui.process.periodic.cron.{CronParameterValidator, CronSchedulePropertyExtractor}
import pl.touk.nussknacker.ui.process.periodic.legacy.db.{LegacyDbInitializer, SlickLegacyPeriodicProcessesRepository}
import pl.touk.nussknacker.ui.process.repository.SlickPeriodicProcessesRepository

import java.time.Clock

object PeriodicDeploymentManagerDecorator extends LazyLogging {

  def decorate(
      underlying: DeploymentManager,
      schedulingSupported: SchedulingSupported,
      deploymentConfig: Config,
      dependencies: DeploymentManagerDependencies,
      schedulingDeps: SchedulingDependencies,
  ): DeploymentManager = {
    logger.info("Decorating DM with periodic functionality")
    import dependencies._
    import net.ceedubs.ficus.Ficus._
    import net.ceedubs.ficus.readers.ArbitraryTypeReader._

    val clock = Clock.systemDefaultZone()

    deploymentConfig.as[SchedulingConfig]("scheduling")
    val rawSchedulingConfig = deploymentConfig.getConfig("scheduling")
    val schedulingConfig    = rawSchedulingConfig.as[SchedulingConfig]

    val schedulePropertyExtractorFactory: SchedulePropertyExtractorFactory =
      schedulingSupported.customSchedulePropertyExtractorFactory
        .getOrElse(_ => CronSchedulePropertyExtractor())

    val processConfigEnricherFactory =
      schedulingSupported.customProcessConfigEnricherFactory
        .getOrElse(ProcessConfigEnricherFactory.noOp)

    val periodicProcessListenerFactory =
      schedulingSupported.customScheduledProcessListenerFactory
        .getOrElse(EmptyScheduledProcessListenerFactory)

    val additionalDeploymentDataProvider =
      schedulingSupported.customAdditionalDeploymentDataProvider
        .getOrElse(DefaultAdditionalDeploymentDataProvider)

    val periodicProcessesRepository = schedulingConfig.legacyDb match {
      case None =>
        new SlickPeriodicProcessesRepository(
          schedulingConfig.processingType,
          schedulingDeps.dbRef.db,
          schedulingDeps.dbRef.profile,
          clock,
          schedulingDeps.fetchingProcessRepository
        )
      case Some(customDbConfig) =>
        val (db, profile) = LegacyDbInitializer.init(customDbConfig)
        new SlickLegacyPeriodicProcessesRepository(
          schedulingConfig.processingType,
          db,
          profile,
          clock,
          schedulingDeps.fetchingProcessRepository
        )
    }

    PeriodicDeploymentManager(
      delegate = underlying,
      dmDependencies = dependencies,
      schedulingDependencies = schedulingDeps,
      periodicProcessesRepository = periodicProcessesRepository,
      scheduledExecutionPerformer = schedulingSupported.createScheduledExecutionPerformer(rawSchedulingConfig),
      schedulePropertyExtractorFactory = schedulePropertyExtractorFactory,
      processConfigEnricherFactory = processConfigEnricherFactory,
      listenerFactory = periodicProcessListenerFactory,
      schedulingConfig = schedulingConfig,
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
