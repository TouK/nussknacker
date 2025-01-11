package pl.touk.nussknacker.ui.process.periodic

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.DeploymentManagerDependencies
import pl.touk.nussknacker.engine.api.component.ScenarioPropertyConfig
import pl.touk.nussknacker.engine.api.definition.{MandatoryParameterValidator, StringParameterEditor}
import pl.touk.nussknacker.engine.api.deployment.{DeploymentManager, PeriodicDeploymentHandler}
import pl.touk.nussknacker.ui.db.DbRef
import pl.touk.nussknacker.ui.process.periodic.cron.CronParameterValidator
import pl.touk.nussknacker.ui.process.periodic.legacy.db.{LegacyDbInitializer, SlickLegacyPeriodicProcessesRepository}
import pl.touk.nussknacker.ui.process.periodic.service.{
  DefaultAdditionalDeploymentDataProvider,
  EmptyPeriodicProcessListenerFactory,
  ProcessConfigEnricherFactory
}
import pl.touk.nussknacker.ui.process.repository.{
  DBFetchingProcessRepository,
  DbScenarioActionReadOnlyRepository,
  ScenarioLabelsRepository,
  SlickPeriodicProcessesRepository
}
import slick.jdbc
import slick.jdbc.JdbcProfile

import java.time.Clock

class PeriodicDeploymentManagerProvider(
    underlying: DeploymentManager,
    handler: PeriodicDeploymentHandler,
    deploymentConfig: Config,
    dependencies: DeploymentManagerDependencies,
    dbRef: DbRef,
    clock: Clock,
) extends LazyLogging {

  def provide(): DeploymentManager = {
    logger.info("Decorating DM with periodic functionality")
    import net.ceedubs.ficus.Ficus._
    import net.ceedubs.ficus.readers.ArbitraryTypeReader._
    val periodicBatchConfig              = deploymentConfig.as[PeriodicBatchConfig]("deploymentManager")
    val periodicProcessesManagerProvider = createPeriodicProcessesManagerProvider(periodicBatchConfig)

    PeriodicDeploymentManager(
      delegate = underlying,
      periodicDeploymentHandler = handler,
      schedulePropertyExtractorFactory = _ => CronSchedulePropertyExtractor(),
      processConfigEnricherFactory = ProcessConfigEnricherFactory.noOp,
      periodicBatchConfig = periodicBatchConfig,
      originalConfig = deploymentConfig,
      EmptyPeriodicProcessListenerFactory,
      DefaultAdditionalDeploymentDataProvider,
      dependencies,
      periodicProcessesManagerProvider.provide(periodicBatchConfig.processingType)
    )
  }

  private def createPeriodicProcessesManagerProvider(
      periodicBatchConfig: PeriodicBatchConfig,
  ): PeriodicProcessesManagerProvider = {
    import dependencies._
    val actionRepository =
      DbScenarioActionReadOnlyRepository.create(dbRef)
    val scenarioLabelsRepository =
      new ScenarioLabelsRepository(dbRef)
    val processRepository =
      DBFetchingProcessRepository.createFutureRepository(dbRef, actionRepository, scenarioLabelsRepository)

    val periodicProcessesRepository = periodicBatchConfig.db match {
      case None =>
        new SlickPeriodicProcessesRepository(dbRef.db, dbRef.profile, clock)
      case Some(customDbConfig) =>
        val clock                                                      = Clock.systemDefaultZone()
        val (db: jdbc.JdbcBackend.DatabaseDef, dbProfile: JdbcProfile) = LegacyDbInitializer.init(customDbConfig)
        new SlickLegacyPeriodicProcessesRepository(db, dbProfile, clock)
    }

    (processingType: String) =>
      new RepositoryBasedPeriodicProcessesManager(
        processingType,
        periodicProcessesRepository,
        processRepository,
      )
  }

}

object PeriodicDeploymentManagerProvider {

  private val cronConfig = CronSchedulePropertyExtractor.CronPropertyDefaultName -> ScenarioPropertyConfig(
    defaultValue = None,
    editor = Some(StringParameterEditor),
    validators = Some(List(MandatoryParameterValidator, CronParameterValidator.delegate)),
    label = Some("Schedule"),
    hintText = Some("Quartz cron syntax. You can specify multiple schedulers separated by '|'.")
  )

  def additionalScenarioProperties(config: Config): Map[String, ScenarioPropertyConfig] =
    Map(cronConfig)

}
