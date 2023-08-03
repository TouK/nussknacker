package pl.touk.nussknacker.engine.management.periodic

import akka.actor.ActorSystem
import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.BaseModelData
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.api.deployment.inconsistency.InconsistentStateDetector
import pl.touk.nussknacker.engine.api.process.{ProcessIdWithName, ProcessName}
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
    getProcessStates(processVersion.processName)(DataFreshnessPolicy.Fresh)
      .map(_.value)
      .map(InconsistentStateDetector.extractAtMostOneStatus)
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

  override def getProcessStates(name: ProcessName)(implicit freshnessPolicy: DataFreshnessPolicy): Future[WithDataFreshnessStatus[List[StatusDetails]]] = {
    for {
      delegateState <- delegate.getProcessStates(name)
      mergedStatus <- service.mergeStatusWithDeployments(name, InconsistentStateDetector.extractAtMostOneStatus(delegateState.value))
    } yield WithDataFreshnessStatus(mergedStatus.toList, delegateState.cached)
  }

  override def getProcessState(idWithName: ProcessIdWithName, lastStateAction: Option[ProcessAction])(implicit freshnessPolicy: DataFreshnessPolicy): Future[WithDataFreshnessStatus[ProcessState]] = {
    for {
      statusesWithFreshness <- getProcessStates(idWithName.name)
      _ = logger.debug(s"Statuses for ${idWithName.name}: $statusesWithFreshness")
      actionAfterPostprocessOpt <- {
        delegate match {
          case postprocessing: PostprocessingProcessStatus =>
            postprocessing.postprocess(idWithName, statusesWithFreshness.value)
          case _ => Future.successful(None)
        }
      }
      engineStateResolvedWithLastAction = flattenStatus(actionAfterPostprocessOpt.orElse(lastStateAction), statusesWithFreshness.value)
    } yield statusesWithFreshness.copy(value = processStateDefinitionManager.processState(engineStateResolvedWithLastAction))
  }

  protected def flattenStatus(lastStateAction: Option[ProcessAction], statusDetailsList: List[StatusDetails]): StatusDetails = {
    // InconsistentStateDetector is a little overkill here. It checks some things that won't happen in periodic case because scheduler
    // is in the same jvm as designer. Also we have some synchronization logic that makes those inconsistencies impossible.
    // After cleanup in scheduler mechanism, we should remove this
    new InconsistentStateDetector {
      override protected def isFollowingDeployStatus(state: StatusDetails): Boolean = {
        IsFollowingDeployStatusDeterminer.isFollowingDeployStatus(state.status)
      }
    }.resolve(statusDetailsList, lastStateAction)
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

  override def invokeCustomAction(actionRequest: CustomActionRequest, canonicalProcess: CanonicalProcess): Future[CustomActionResult] =
    customActionsProvider.invokeCustomAction(actionRequest, canonicalProcess)
}
