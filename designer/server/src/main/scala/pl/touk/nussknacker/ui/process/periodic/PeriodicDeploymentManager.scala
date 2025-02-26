package pl.touk.nussknacker.ui.process.periodic

import cats.data.OptionT
import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.DeploymentManagerDependencies
import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.api.deployment.scheduler.model.{ScheduleProperty => ApiScheduleProperty}
import pl.touk.nussknacker.engine.api.deployment.scheduler.services._
import pl.touk.nussknacker.engine.api.process.{ProcessIdWithName, ProcessName}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.deployment.ExternalDeploymentId
import pl.touk.nussknacker.ui.process.periodic.Utils._
import pl.touk.nussknacker.ui.process.repository.PeriodicProcessesRepository

import java.time.{Clock, Instant}
import scala.concurrent.{ExecutionContext, Future}

object PeriodicDeploymentManager {

  def apply(
      delegate: DeploymentManager,
      scheduledExecutionPerformer: ScheduledExecutionPerformer,
      schedulePropertyExtractorFactory: SchedulePropertyExtractorFactory,
      processConfigEnricherFactory: ProcessConfigEnricherFactory,
      schedulingConfig: SchedulingConfig,
      originalConfig: Config,
      listenerFactory: ScheduledProcessListenerFactory,
      additionalDeploymentDataProvider: AdditionalDeploymentDataProvider,
      dependencies: DeploymentManagerDependencies,
      periodicProcessesRepository: PeriodicProcessesRepository,
  ): PeriodicDeploymentManager = {
    import dependencies._

    val clock                 = Clock.systemDefaultZone()
    val listener              = listenerFactory.create(originalConfig)
    val processConfigEnricher = processConfigEnricherFactory(originalConfig)
    val service = new PeriodicProcessService(
      delegate,
      scheduledExecutionPerformer,
      periodicProcessesRepository,
      listener,
      additionalDeploymentDataProvider,
      schedulingConfig.deploymentRetry,
      schedulingConfig.executionConfig,
      schedulingConfig.maxFetchedPeriodicScenarioActivities,
      processConfigEnricher,
      clock,
      dependencies.actionService,
      dependencies.configsFromProvider,
    )

    // These actors have to be created with retries because they can initially fail to create due to taken names,
    // if the actors (with the same names) created before reload aren't fully stopped (and their names freed) yet
    val deploymentActor = createActorWithRetry(
      s"periodic-${schedulingConfig.processingType}-deployer",
      DeploymentActor.props(service, schedulingConfig.deployInterval),
      dependencies.actorSystem
    )
    val rescheduleFinishedActor = createActorWithRetry(
      s"periodic-${schedulingConfig.processingType}-rescheduler",
      RescheduleFinishedActor.props(service, schedulingConfig.rescheduleCheckInterval),
      dependencies.actorSystem
    )

    val toClose = () => {
      runSafely(listener.close())
      // deploymentActor and rescheduleFinishedActor just call methods from PeriodicProcessService on interval,
      // they don't have any internal state, so stopping them non-gracefully is safe
      runSafely(dependencies.actorSystem.stop(deploymentActor))
      runSafely(dependencies.actorSystem.stop(rescheduleFinishedActor))
    }
    new PeriodicDeploymentManager(
      delegate,
      service,
      periodicProcessesRepository,
      schedulePropertyExtractorFactory(originalConfig),
      toClose
    )
  }

}

class PeriodicDeploymentManager private[periodic] (
    val delegate: DeploymentManager,
    service: PeriodicProcessService,
    periodicProcessesRepository: PeriodicProcessesRepository,
    schedulePropertyExtractor: SchedulePropertyExtractor,
    toClose: () => Unit
)(implicit val ec: ExecutionContext)
    extends DeploymentManager
    with ManagerSpecificScenarioActivitiesStoredByManager
    with LazyLogging {

  import periodicProcessesRepository._

  override def processCommand[Result](command: DMScenarioCommand[Result]): Future[Result] =
    command match {
      case command: DMValidateScenarioCommand => validate(command)
      case command: DMRunDeploymentCommand    => runDeployment(command)
      case command: DMCancelScenarioCommand   => cancelScenario(command)
      case command: DMStopScenarioCommand     => stopScenario(command)
      case command: DMRunOffScheduleCommand   => actionInstantBatch(command)
      case _: DMTestScenarioCommand | _: DMCancelDeploymentCommand | _: DMStopDeploymentCommand |
          _: DMMakeScenarioSavepointCommand =>
        delegate.processCommand(command)
    }

  private def validate(command: DMValidateScenarioCommand): Future[Unit] = {
    import command._
    for {
      scheduledProperty <- extractScheduleProperty(canonicalProcess)
      _                 <- Future.fromTry(service.prepareInitialScheduleDates(scheduledProperty).toTry)
      _ <- delegate.processCommand(
        DMValidateScenarioCommand(processVersion, deploymentData, canonicalProcess, updateStrategy)
      )
    } yield ()
  }

  private def runDeployment(command: DMRunDeploymentCommand): Future[Option[ExternalDeploymentId]] = {
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
          cancelScenario(DMCancelScenarioCommand(processVersion.processName, deploymentData.user))
        )
        .map(_ => None)
    }
  }

  private def extractScheduleProperty(canonicalProcess: CanonicalProcess): Future[ScheduleProperty] = {
    schedulePropertyExtractor(canonicalProcess) match {
      case Right(scheduleProperty) =>
        Future.successful(toDomain(scheduleProperty))
      case Left(error) =>
        Future.failed(new PeriodicProcessException(error))
    }
  }

  private def toDomain(
      apiScheduleProperty: ApiScheduleProperty,
  ): ScheduleProperty = apiScheduleProperty match {
    case property: ApiScheduleProperty.SingleScheduleProperty =>
      toDomain(property)
    case ApiScheduleProperty.MultipleScheduleProperty(schedules) =>
      MultipleScheduleProperty(schedules.map { case (k, v) => (k, toDomain(v)) })
  }

  private def toDomain(
      apiSingleScheduleProperty: ApiScheduleProperty.SingleScheduleProperty
  ): SingleScheduleProperty = apiSingleScheduleProperty match {
    case ApiScheduleProperty.CronScheduleProperty(labelOrCronExpr) => CronScheduleProperty(labelOrCronExpr)
  }

  private def stopScenario(command: DMStopScenarioCommand): Future[SavepointResult] = {
    import command._
    service.deactivate(scenarioName).flatMap { deploymentIdsToStop =>
      // TODO: should return List of SavepointResult
      Future
        .sequence(
          deploymentIdsToStop
            .map(deploymentId =>
              delegate.processCommand(DMStopDeploymentCommand(scenarioName, deploymentId, savepointDir, user))
            )
        )
        .map(_.headOption.getOrElse {
          throw new IllegalStateException(s"No running deployment for scenario: $scenarioName found")
        })
    }
  }

  private def cancelScenario(command: DMCancelScenarioCommand): Future[Unit] = {
    import command._
    service.deactivate(scenarioName).flatMap { deploymentIdsToCancel =>
      Future
        .sequence(
          deploymentIdsToCancel
            .map(deploymentId => delegate.processCommand(DMCancelDeploymentCommand(scenarioName, deploymentId, user)))
        )
        .map(_ => ())
    }
  }

  override def deploymentsStatusesQueryForAllScenariosSupport: DeploymentsStatusesQueryForAllScenariosSupport =
    service.deploymentsStatusesQueryForAllScenariosSupport

  override def getScenarioDeploymentsStatuses(
      scenarioName: ProcessName
  )(implicit freshnessPolicy: DataFreshnessPolicy): Future[WithDataFreshnessStatus[List[DeploymentStatusDetails]]] = {
    service.getMergedStatusDetails(scenarioName).map(_.map(List(_)))
  }

  override def processStateDefinitionManager: ProcessStateDefinitionManager =
    new PeriodicProcessStateDefinitionManager(delegate.processStateDefinitionManager)

  override def close(): Unit = {
    logger.info("Closing periodic process manager")
    toClose()
    delegate.close()
  }

  // TODO We don't handle deployment synchronization on periodic DM because it currently uses it's own deployments and
  //      its statuses synchronization mechanism (see PeriodicProcessService.synchronizeDeploymentsStates)
  //      We should move periodic mechanism to the core and reuse new synchronization mechanism also in this case.
  override def deploymentSynchronisationSupport: DeploymentSynchronisationSupport = NoDeploymentSynchronisationSupport

  // TODO: NU-1772
  //  In the current implementation:
  //    - PeriodicDeploymentManager is a kind of plugin, and it has its own data source (separate db)
  //    - PeriodicDeploymentManager returns (by implementing ManagerSpecificScenarioActivitiesStoredByManager) custom ScenarioActivities, that are associated with operations performed internally by the manager
  //  Why is it not the ideal solution:
  //    - we have different data sources for ScenarioActivities, and merging data from two sources may be problematic, e.g. when paginating results
  //  How can it be redesigned:
  //    - we could do it using the ManagerSpecificScenarioActivitiesStoredByNussknacker instead
  //    - that way, Nu would provide hooks, that would allow the manager to save and modify its custom activities in the Nu database
  //    - only the Nussknacker database would then be used, as single source of Scenario Activities
  //  Why not implemented that way in the first place?
  //    - we have to migrate information about old periodic deployments, or decide that we don't need it
  //    - we have to modify the logic of the PeriodicDeploymentManager
  //    - we may need to refactor PeriodicDeploymentManager data source first

  override def managerSpecificScenarioActivities(
      processIdWithName: ProcessIdWithName,
      after: Option[Instant],
  ): Future[List[ScenarioActivity]] =
    service.getScenarioActivitiesSpecificToPeriodicProcess(processIdWithName, after)

  private def actionInstantBatch(command: DMRunOffScheduleCommand): Future[RunOffScheduleResult] = {
    val processName           = command.processVersion.processName
    val instantScheduleResult = instantSchedule(processName)
    instantScheduleResult
      .map(_ => RunOffScheduleResult(s"Scenario ${processName.value} scheduled for immediate start"))
      .getOrElse(RunOffScheduleResult(s"Failed to schedule $processName to run as instant batch"))
  }

  // TODO: Why we don't allow running not scheduled scenario? Maybe we can try to schedule it?
  private def instantSchedule(processName: ProcessName): OptionT[Future, Unit] = for {
    // schedule for immediate run
    processDeployment <- OptionT(
      service
        .getLatestDeploymentsForActiveSchedules(processName)
        .map(_.groupedByPeriodicProcess.headOption.flatMap(_.deployments.headOption))
    )
    processDeploymentWithProcessJson <- OptionT.liftF(
      periodicProcessesRepository.findProcessData(processDeployment.id).run
    )
    _ <- OptionT.liftF(service.deploy(processDeploymentWithProcessJson))
  } yield ()

  override def schedulingSupport: SchedulingSupport = NoSchedulingSupport
}
