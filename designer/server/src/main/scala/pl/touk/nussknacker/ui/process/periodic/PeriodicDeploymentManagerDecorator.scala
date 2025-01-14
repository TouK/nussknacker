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
import pl.touk.nussknacker.ui.db.DbRef
import pl.touk.nussknacker.ui.process.periodic.cron.{CronParameterValidator, CronSchedulePropertyExtractor}
import pl.touk.nussknacker.ui.process.periodic.legacy.db.{LegacyDbInitializer, SlickLegacyPeriodicProcessesRepository}
import pl.touk.nussknacker.ui.process.repository.{
  DBFetchingProcessRepository,
  DbScenarioActionReadOnlyRepository,
  ScenarioLabelsRepository,
  SlickPeriodicProcessesRepository
}
import slick.jdbc
import slick.jdbc.JdbcProfile

import java.time.Clock

object PeriodicDeploymentManagerDecorator extends LazyLogging {

  def decorate(
      underlying: DeploymentManager,
      periodicExecutionSupported: PeriodicExecutionSupported,
      modelData: ModelData,
      deploymentConfig: Config,
      dependencies: DeploymentManagerDependencies,
      dbRef: DbRef,
      clock: Clock,
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
      periodicProcessesManager = createPeriodicProcessesManager(
        periodicBatchConfig.processingType,
        periodicBatchConfig,
        dependencies,
        dbRef,
        clock
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

  private def createPeriodicProcessesManager(
      processingType: String,
      periodicBatchConfig: PeriodicBatchConfig,
      dependencies: DeploymentManagerDependencies,
      dbRef: DbRef,
      clock: Clock,
  ): PeriodicProcessesManager = {
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
    new RepositoryBasedPeriodicProcessesManager(
      processingType,
      periodicProcessesRepository,
      processRepository,
    )
  }

  private val cronConfig = CronSchedulePropertyExtractor.CronPropertyDefaultName -> ScenarioPropertyConfig(
    defaultValue = None,
    editor = Some(StringParameterEditor),
    validators = Some(List(MandatoryParameterValidator, CronParameterValidator)),
    label = Some("Schedule"),
    hintText = Some("Quartz cron syntax. You can specify multiple schedulers separated by '|'.")
  )

}
