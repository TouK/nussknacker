package pl.touk.nussknacker.engine.management.periodic

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.api.process.{ProcessIdWithName, ProcessName}
import pl.touk.nussknacker.engine.api.test.ScenarioTestData
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.deployment.{
  CustomActionDefinition,
  CustomActionResult,
  DeploymentData,
  DeploymentId,
  ExternalDeploymentId,
  User
}
import pl.touk.nussknacker.engine.management.FlinkConfig
import pl.touk.nussknacker.engine.management.periodic.PeriodicProcessService.PeriodicProcessStatus
import pl.touk.nussknacker.engine.management.periodic.Utils.runSafely
import pl.touk.nussknacker.engine.management.periodic.db.{DbInitializer, SlickPeriodicProcessesRepository}
import pl.touk.nussknacker.engine.management.periodic.flink.FlinkJarManager
import pl.touk.nussknacker.engine.management.periodic.service.{
  AdditionalDeploymentDataProvider,
  PeriodicProcessListenerFactory,
  ProcessConfigEnricherFactory
}
import pl.touk.nussknacker.engine.testmode.TestProcess
import pl.touk.nussknacker.engine.{BaseModelData, DeploymentManagerDependencies}
import slick.jdbc
import slick.jdbc.JdbcProfile

import java.time.Clock
import scala.concurrent.{ExecutionContext, Future}

object PeriodicDeploymentManager {

  def apply(
      delegate: DeploymentManager,
      schedulePropertyExtractorFactory: SchedulePropertyExtractorFactory,
      processConfigEnricherFactory: ProcessConfigEnricherFactory,
      periodicBatchConfig: PeriodicBatchConfig,
      flinkConfig: FlinkConfig,
      originalConfig: Config,
      modelData: BaseModelData,
      listenerFactory: PeriodicProcessListenerFactory,
      additionalDeploymentDataProvider: AdditionalDeploymentDataProvider,
      customActionsProviderFactory: PeriodicCustomActionsProviderFactory,
      dependencies: DeploymentManagerDependencies
  ): PeriodicDeploymentManager = {
    import dependencies._

    val clock = Clock.systemDefaultZone()

    val (db: jdbc.JdbcBackend.DatabaseDef, dbProfile: JdbcProfile) = DbInitializer.init(periodicBatchConfig.db)
    val scheduledProcessesRepository =
      new SlickPeriodicProcessesRepository(db, dbProfile, clock, periodicBatchConfig.processingType)
    val jarManager            = FlinkJarManager(flinkConfig, periodicBatchConfig, modelData)
    val listener              = listenerFactory.create(originalConfig)
    val processConfigEnricher = processConfigEnricherFactory(originalConfig)
    val service = new PeriodicProcessService(
      delegate,
      jarManager,
      scheduledProcessesRepository,
      listener,
      additionalDeploymentDataProvider,
      periodicBatchConfig.deploymentRetry,
      periodicBatchConfig.executionConfig,
      processConfigEnricher,
      clock,
      dependencies.deploymentService
    )
    val deploymentActor = dependencies.actorSystem.actorOf(
      DeploymentActor.props(service, periodicBatchConfig.deployInterval),
      s"periodic-${periodicBatchConfig.processingType}-deployer"
    )
    val rescheduleFinishedActor = dependencies.actorSystem.actorOf(
      RescheduleFinishedActor.props(service, periodicBatchConfig.rescheduleCheckInterval),
      s"periodic-${periodicBatchConfig.processingType}-rescheduler"
    )

    val customActionsProvider = customActionsProviderFactory.create(scheduledProcessesRepository, service)

    val toClose = () => {
      runSafely(listener.close())
      runSafely(dependencies.actorSystem.stop(deploymentActor))
      runSafely(dependencies.actorSystem.stop(rescheduleFinishedActor))
      runSafely(db.close())
    }
    new PeriodicDeploymentManager(
      delegate,
      service,
      schedulePropertyExtractorFactory(originalConfig),
      customActionsProvider,
      toClose
    )
  }

}

class PeriodicDeploymentManager private[periodic] (
    val delegate: DeploymentManager,
    service: PeriodicProcessService,
    schedulePropertyExtractor: SchedulePropertyExtractor,
    customActionsProvider: PeriodicCustomActionsProvider,
    toClose: () => Unit
)(implicit val ec: ExecutionContext)
    extends DeploymentManager
    with LazyLogging {

  override def processCommand[Result](command: ScenarioCommand[Result]): Future[Result] =
    command match {
      case command: ValidateScenarioCommand => validate(command)
      case command: RunDeploymentCommand    => runDeployment(command)
      case command: CancelScenarioCommand   => cancelScenario(command)
      case command: StopScenarioCommand     => stopScenario(command)
      case command: CustomActionCommand     => customActionsProvider.invokeCustomAction(command)
      case _: TestScenarioCommand | _: CancelDeploymentCommand | _: StopDeploymentCommand |
          _: MakeScenarioSavepointCommand | _: CustomActionCommand =>
        delegate.processCommand(command)
    }

  private def validate(command: ValidateScenarioCommand): Future[Unit] = {
    import command._
    for {
      scheduledProperty <- extractScheduleProperty(canonicalProcess)
      _                 <- Future.fromTry(service.prepareInitialScheduleDates(scheduledProperty).toTry)
      _ <- delegate.processCommand(ValidateScenarioCommand(processVersion, deploymentData, canonicalProcess))
    } yield ()
  }

  private def runDeployment(command: RunDeploymentCommand): Future[Option[ExternalDeploymentId]] = {
    import command._
    extractScheduleProperty(canonicalProcess).flatMap { scheduleProperty =>
      logger.info(s"About to (re)schedule ${processVersion.processName} in version ${processVersion.versionId}")
      // PeriodicProcessStateDefinitionManager do not allow to redeploy (so doesn't GUI),
      // but NK API does, so we need to handle this situation.
      service
        .schedule(
          scheduleProperty,
          processVersion,
          canonicalProcess,
          deploymentData.deploymentId.toActionIdOpt.getOrElse(
            throw new IllegalArgumentException(s"deploymentData.deploymentId should be valid ProcessActionId")
          ),
          cancelScenario(CancelScenarioCommand(processVersion.processName, deploymentData.user))
        )
        .map(_ => None)
    }
  }

  private def extractScheduleProperty(canonicalProcess: CanonicalProcess): Future[ScheduleProperty] = {
    schedulePropertyExtractor(canonicalProcess) match {
      case Right(scheduleProperty) =>
        Future.successful(scheduleProperty)
      case Left(error) =>
        Future.failed(new PeriodicProcessException(error))
    }
  }

  private def stopScenario(command: StopScenarioCommand): Future[SavepointResult] = {
    import command._
    service.deactivate(scenarioName).flatMap { deploymentIdsToStop =>
      // TODO: should return List of SavepointResult
      Future
        .sequence(
          deploymentIdsToStop
            .map(deploymentId =>
              delegate.processCommand(StopDeploymentCommand(scenarioName, deploymentId, savepointDir, user))
            )
        )
        .map(_.headOption.getOrElse {
          throw new IllegalStateException(s"No running deployment for scenario: $scenarioName found")
        })
    }
  }

  private def cancelScenario(command: CancelScenarioCommand): Future[Unit] = {
    import command._
    service.deactivate(scenarioName).flatMap { deploymentIdsToCancel =>
      Future
        .sequence(
          deploymentIdsToCancel
            .map(deploymentId => delegate.processCommand(CancelDeploymentCommand(scenarioName, deploymentId, user)))
        )
        .map(_ => ())
    }
  }

  override def getProcessStates(
      name: ProcessName
  )(implicit freshnessPolicy: DataFreshnessPolicy): Future[WithDataFreshnessStatus[List[StatusDetails]]] = {
    service.getStatusDetails(name).map(_.map(List(_)))
  }

  override def resolve(
      idWithName: ProcessIdWithName,
      statusDetailsList: List[StatusDetails],
      lastStateAction: Option[ProcessAction]
  ): Future[ProcessState] = {
    val statusDetails = statusDetailsList.head
    // TODO: add "real" presentation of deployments in GUI
    val mergedStatus = processStateDefinitionManager
      .processState(
        statusDetails.copy(status = statusDetails.status.asInstanceOf[PeriodicProcessStatus].mergedStatusDetails.status)
      )
    Future.successful(mergedStatus.copy(tooltip = processStateDefinitionManager.statusTooltip(statusDetails.status)))
  }

  override def processStateDefinitionManager: ProcessStateDefinitionManager =
    new PeriodicProcessStateDefinitionManager(delegate.processStateDefinitionManager)

  override def close(): Unit = {
    logger.info("Closing periodic process manager")
    toClose()
    delegate.close()
  }

  override def customActionsDefinitions: List[CustomActionDefinition] = customActionsProvider.customActions

}
