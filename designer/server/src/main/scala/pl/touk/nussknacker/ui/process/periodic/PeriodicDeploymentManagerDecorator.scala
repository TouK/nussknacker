package pl.touk.nussknacker.ui.process.periodic

import cats.implicits.catsSyntaxOptionId
import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.DeploymentManagerDependencies
import pl.touk.nussknacker.engine.api.component.ScenarioPropertyConfig
import pl.touk.nussknacker.engine.api.definition.{MandatoryParameterValidator, StaticStringParameterEditor}
import pl.touk.nussknacker.engine.api.deployment.{DeploymentManager, NoSchedulingSupport, SchedulingSupported}
import pl.touk.nussknacker.engine.api.deployment.scheduler.services.{
  EmptyScheduledProcessListenerFactory,
  ProcessConfigEnricherFactory,
  SchedulePropertyExtractorFactory
}
import pl.touk.nussknacker.ui.process.periodic.cron.{CronParameterValidator, CronSchedulePropertyExtractor}
import pl.touk.nussknacker.ui.process.repository.SlickPeriodicProcessesRepository

import java.time.Clock

object PeriodicDeploymentManagerDecorator extends LazyLogging {

  def decorateIfSchedulingSupported(
      underlying: DeploymentManager,
      deploymentConfig: Config,
      dependencies: DeploymentManagerDependencies,
      schedulingDepsOpt: Option[SchedulingDependencies]
  ): (DeploymentManager, Map[String, ScenarioPropertyConfig]) = {
    val schedulingForProcessingType = determineIfSchedulingIsAvailable(deploymentConfig, schedulingDepsOpt)
    schedulingForProcessingType match {
      case SchedulingForProcessingType.Available(schedulingDeps) =>
        underlying.schedulingSupport match {
          case supported: SchedulingSupported =>
            val decoratedManager = PeriodicDeploymentManagerDecorator.decorate(
              underlying = underlying,
              schedulingSupported = supported,
              deploymentConfig = deploymentConfig,
              dependencies = dependencies,
              schedulingDeps = schedulingDeps,
            )
            (decoratedManager, PeriodicDeploymentManagerDecorator.additionalScenarioProperties)
          case NoSchedulingSupport =>
            throw new IllegalStateException(
              s"DeploymentManager ${underlying.getClass.getName} does not support periodic execution"
            )
        }
      case SchedulingForProcessingType.NotAvailable =>
        (underlying, Map.empty[String, ScenarioPropertyConfig])
    }
  }

  private def determineIfSchedulingIsAvailable(
      deploymentConfig: Config,
      schedulingDepsOpt: Option[SchedulingDependencies]
  ): SchedulingForProcessingType = {
    if (deploymentConfig.hasPath("scheduling") &&
      deploymentConfig.getBoolean("scheduling.enabled")) {
      val schedulingDeps = schedulingDepsOpt.getOrElse(
        throw new RuntimeException(
          s"Scheduling dependencies not present, but required for Deployment Manager with scheduling enabled"
        )
      )
      SchedulingForProcessingType.Available(schedulingDeps)
    } else {
      SchedulingForProcessingType.NotAvailable
    }
  }

  private def decorate(
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

    schedulingDeps.periodicLock.lockDuration.foreach { lockDuration =>
      if (lockDuration < schedulingConfig.deployInterval)
        throw new IllegalArgumentException(
          s"ha.periodicLockDuration ($lockDuration) must be >= scheduling.deployInterval=${schedulingConfig.deployInterval}"
        )
      if (lockDuration < schedulingConfig.rescheduleCheckInterval)
        throw new IllegalArgumentException(
          s"ha.periodicLockDuration ($lockDuration) must be >= scheduling.rescheduleCheckInterval=${schedulingConfig.rescheduleCheckInterval}"
        )
    }

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

    val periodicProcessesRepository = new SlickPeriodicProcessesRepository(
      schedulingConfig.processingType,
      schedulingDeps.dbRef.db,
      schedulingDeps.dbRef.profile,
      clock,
      schedulingDeps.fetchingProcessRepository
    )

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
    editor = StaticStringParameterEditor.some,
    validators = Some(List(MandatoryParameterValidator, CronParameterValidator)),
    label = Some("Schedule"),
    hintText = Some("Quartz cron syntax. You can specify multiple schedulers separated by '|'.")
  )

  private sealed trait SchedulingForProcessingType

  private object SchedulingForProcessingType {

    case object NotAvailable extends SchedulingForProcessingType

    final case class Available(deps: SchedulingDependencies) extends SchedulingForProcessingType

  }

}
