package pl.touk.nussknacker.engine.management.periodic

import akka.actor.ActorSystem
import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.BaseModelData
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.api.test.ScenarioTestData
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.deployment.{DeploymentData, ExternalDeploymentId, User}
import pl.touk.nussknacker.engine.management.FlinkConfig
import pl.touk.nussknacker.engine.management.periodic.Utils.runSafely
import pl.touk.nussknacker.engine.management.periodic.db.{DbInitializer, SlickPeriodicProcessesRepository}
import pl.touk.nussknacker.engine.management.periodic.flink.FlinkJarManager
import pl.touk.nussknacker.engine.management.periodic.service.{AdditionalDeploymentDataProvider, PeriodicProcessListenerFactory, ProcessConfigEnricherFactory}
import pl.touk.nussknacker.engine.testmode.TestProcess
import slick.jdbc
import slick.jdbc.JdbcProfile
import sttp.client3.SttpBackend

import java.time.Clock
import scala.concurrent.{ExecutionContext, Future}

object PeriodicDeploymentManager {

  def apply(delegate: DeploymentManager,
            schedulePropertyExtractorFactory: SchedulePropertyExtractorFactory,
            processConfigEnricherFactory: ProcessConfigEnricherFactory,
            periodicBatchConfig: PeriodicBatchConfig,
            flinkConfig: FlinkConfig,
            originalConfig: Config,
            modelData: BaseModelData,
            listenerFactory: PeriodicProcessListenerFactory,
            additionalDeploymentDataProvider: AdditionalDeploymentDataProvider,
            customActionsProviderFactory: PeriodicCustomActionsProviderFactory)
           (implicit ec: ExecutionContext, system: ActorSystem, sttpBackend: SttpBackend[Future, Any]): PeriodicDeploymentManager = {

    val clock = Clock.systemDefaultZone()

    val (db: jdbc.JdbcBackend.DatabaseDef, dbProfile: JdbcProfile) = DbInitializer.init(periodicBatchConfig.db)
    val scheduledProcessesRepository = new SlickPeriodicProcessesRepository(db, dbProfile, clock, periodicBatchConfig.processingType)
    val jarManager = FlinkJarManager(flinkConfig, periodicBatchConfig, modelData)
    val listener = listenerFactory.create(originalConfig)
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
      clock
    )
    val deploymentActor = system.actorOf(DeploymentActor.props(service, periodicBatchConfig.deployInterval))
    val rescheduleFinishedActor = system.actorOf(RescheduleFinishedActor.props(service, periodicBatchConfig.rescheduleCheckInterval))

    val customActionsProvider = customActionsProviderFactory.create(scheduledProcessesRepository, service)

    val toClose = () => {
      runSafely(listener.close())
      system.stop(deploymentActor)
      system.stop(rescheduleFinishedActor)
      db.close()
    }
    new PeriodicDeploymentManager(delegate, service, schedulePropertyExtractorFactory(originalConfig), customActionsProvider, toClose)
  }
}

class PeriodicDeploymentManager private[periodic](val delegate: DeploymentManager,
                                                  service: PeriodicProcessService,
                                                  schedulePropertyExtractor: SchedulePropertyExtractor,
                                                  customActionsProvider: PeriodicCustomActionsProvider,
                                                  toClose: () => Unit)
                                                 (implicit val ec: ExecutionContext)
  extends DeploymentManager with LazyLogging {


  override def validate(processVersion: ProcessVersion, deploymentData: DeploymentData, canonicalProcess: CanonicalProcess): Future[Unit] = {
    for {
      scheduledProperty <- extractScheduleProperty(canonicalProcess)
      _ <- Future.fromTry(service.prepareInitialScheduleDates(scheduledProperty).toTry)
      _ <- delegate.validate(processVersion, deploymentData, canonicalProcess)
    } yield ()
  }

  override def deploy(processVersion: ProcessVersion,
                      deploymentData: DeploymentData,
                      canonicalProcess: CanonicalProcess,
                      savepointPath: Option[String]): Future[Option[ExternalDeploymentId]] = {
    extractScheduleProperty(canonicalProcess).flatMap { scheduleProperty =>
      logger.info(s"About to (re)schedule ${processVersion.processName} in version ${processVersion.versionId}")
      // PeriodicProcessStateDefinitionManager do not allow to redeploy (so doesn't GUI),
      // but NK API does, so we need to handle this situation.
      service.schedule(scheduleProperty, processVersion, canonicalProcess, cancelIfJobPresent(processVersion, deploymentData.user))
        .map(_ => None)
    }
  }

  private def extractScheduleProperty[T](canonicalProcess: CanonicalProcess): Future[ScheduleProperty] = {
    schedulePropertyExtractor(canonicalProcess) match {
      case Right(scheduleProperty) =>
        Future.successful(scheduleProperty)
      case Left(error) =>
        Future.failed(new PeriodicProcessException(error))
    }
  }

  private def cancelIfJobPresent(processVersion: ProcessVersion, user: User): Future[Unit] = {
    getProcessState(processVersion.processName)(DataFreshnessPolicy.Fresh)
      .map(_.value)
      .map(_.isDefined)
      .flatMap(shouldStop => {
        if (shouldStop) {
          logger.info(s"Scenario ${processVersion.processName} is running or scheduled. Cancelling before reschedule")
          cancel(processVersion.processName, user).map(_ => ())
        }
        else Future.successful(())
      })
  }

  override def stop(name: ProcessName, savepointDir: Option[String], user: User): Future[SavepointResult] = {
    service.deactivate(name).flatMap {
      _ => delegate.stop(name, savepointDir, user)
    }
  }

  override def cancel(name: ProcessName, user: User): Future[Unit] = {
    service.deactivate(name).flatMap {
      _ => delegate.cancel(name, user)
    }
  }

  override def test[T](name: ProcessName, canonicalProcess: CanonicalProcess, scenarioTestData: ScenarioTestData, variableEncoder: Any => T): Future[TestProcess.TestResults[T]] =
    delegate.test(name, canonicalProcess, scenarioTestData, variableEncoder)

  override def getProcessState(name: ProcessName)(implicit freshnessPolicy: DataFreshnessPolicy): Future[WithDataFreshnessStatus[Option[ProcessState]]] = {
    for {
      delegateState <- delegate.getProcessState(name)
      mergedStatus <- service.mergeStatusWithDeployments(name, delegateState.value)
      formattedByPeriodicManager = mergedStatus.map(processStateDefinitionManager.processState)
    } yield WithDataFreshnessStatus(formattedByPeriodicManager, delegateState.cached)
  }

  override def getProcessState(name: ProcessName, lastAction: Option[ProcessAction])(implicit freshnessPolicy: DataFreshnessPolicy): Future[WithDataFreshnessStatus[Option[ProcessState]]] = {
    for {
      delegateState <- delegate.getProcessState(name, lastAction)
      mergedStatus <- service.mergeStatusWithDeployments(name, delegateState.value)
      formattedByPeriodicManager = mergedStatus.map(processStateDefinitionManager.processState)
    } yield WithDataFreshnessStatus(formattedByPeriodicManager, delegateState.cached)
  }

  override def processStateDefinitionManager: ProcessStateDefinitionManager =
    new PeriodicProcessStateDefinitionManager(delegate.processStateDefinitionManager)

  override def savepoint(name: ProcessName, savepointDir: Option[String]): Future[SavepointResult] = {
    delegate.savepoint(name, savepointDir)
  }

  override def close(): Unit = {
    logger.info("Closing periodic process manager")
    toClose()
    delegate.close()
  }

  override def customActions: List[CustomAction] = customActionsProvider.customActions

  override def invokeCustomAction(actionRequest: CustomActionRequest, canonicalProcess: CanonicalProcess): Future[Either[CustomActionError, CustomActionResult]] =
    customActionsProvider.invokeCustomAction(actionRequest, canonicalProcess)
}
