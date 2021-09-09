package pl.touk.nussknacker.engine.management.periodic

import akka.actor.ActorSystem
import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.management.FlinkConfig
import pl.touk.nussknacker.engine.management.periodic.Utils._
import pl.touk.nussknacker.engine.management.periodic.db.{DbInitializer, SlickPeriodicProcessesRepository}
import pl.touk.nussknacker.engine.management.periodic.flink.FlinkJarManager
import pl.touk.nussknacker.engine.management.periodic.model.{PeriodicProcessDeployment, PeriodicProcessDeploymentStatus}
import pl.touk.nussknacker.engine.management.periodic.service.{AdditionalDeploymentDataProvider, PeriodicProcessListenerFactory}
import slick.jdbc
import slick.jdbc.JdbcProfile
import sttp.client.asynchttpclient.future.AsyncHttpClientFutureBackend
import sttp.client.{NothingT, SttpBackend}

import java.time.{Clock, LocalDateTime, ZoneOffset}
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

object PeriodicDeploymentManager {
  def apply(delegate: DeploymentManager,
            schedulePropertyExtractorFactory: SchedulePropertyExtractorFactory,
            enrichDeploymentWithJarDataFactory: EnrichDeploymentWithJarDataFactory,
            periodicBatchConfig: PeriodicBatchConfig,
            flinkConfig: FlinkConfig,
            originalConfig: Config,
            modelData: ModelData,
            listenerFactory: PeriodicProcessListenerFactory,
            additionalDeploymentDataProvider: AdditionalDeploymentDataProvider): PeriodicDeploymentManager = {
    implicit val system: ActorSystem = ActorSystem("periodic-process-manager-provider")
    implicit val ec: ExecutionContext = ExecutionContext.global
    implicit val backend: SttpBackend[Future, Nothing, NothingT] = AsyncHttpClientFutureBackend.usingConfigBuilder { builder =>
      builder.setThreadPoolName("AsyncBatchPeriodicClient")
    }

    val clock = Clock.systemDefaultZone()

    val (db: jdbc.JdbcBackend.DatabaseDef, dbProfile: JdbcProfile) = DbInitializer.init(periodicBatchConfig.db)
    val scheduledProcessesRepository = new SlickPeriodicProcessesRepository(db, dbProfile, clock)
    val jarManager = FlinkJarManager(flinkConfig, periodicBatchConfig, modelData, enrichDeploymentWithJarDataFactory(originalConfig))
    val listener = listenerFactory.create(originalConfig)
    val service = new PeriodicProcessService(delegate, jarManager, scheduledProcessesRepository, listener, additionalDeploymentDataProvider, clock)
    system.actorOf(DeploymentActor.props(service, periodicBatchConfig.deployInterval))
    system.actorOf(RescheduleFinishedActor.props(service, periodicBatchConfig.rescheduleCheckInterval))

    val toClose = () => {
      runSafely(listener.close())
      Await.ready(system.terminate(), 10 seconds)
      db.close()
      Await.ready(backend.close(), 10 seconds)
      ()
    }
    new PeriodicDeploymentManager(delegate, service, schedulePropertyExtractorFactory(originalConfig), toClose)
  }
}

class PeriodicDeploymentManager(val delegate: DeploymentManager,
                             service: PeriodicProcessService,
                             schedulePropertyExtractor: SchedulePropertyExtractor,
                             toClose: () => Unit)
                            (implicit val ec: ExecutionContext) extends DeploymentManager with LazyLogging {

  override def deploy(processVersion: ProcessVersion,
                      deploymentData: DeploymentData,
                      processDeploymentData: ProcessDeploymentData,
                      savepointPath: Option[String]): Future[Option[ExternalDeploymentId]] = {
    (processDeploymentData, schedulePropertyExtractor(processDeploymentData)) match {
      case (GraphProcess(processJson), Right(scheduleProperty)) =>
        logger.info(s"About to (re)schedule ${processVersion.processName} in version ${processVersion.versionId}")

        // PeriodicProcessStateDefinitionManager do not allow to redeploy (so doesn't GUI),
        // but NK API does, so we need to handle this situation.
        cancelIfJobPresent(processVersion, deploymentData.user)
          .flatMap(_ => {
            logger.info(s"Scheduling ${processVersion.processName}, versionId: ${processVersion.versionId}")
            service.schedule(scheduleProperty, processVersion, processJson)
          }.map(_ => None))
      case (_: GraphProcess, Left(error)) =>
        Future.failed(new PeriodicProcessException(error))
      case _ =>
        Future.failed(new PeriodicProcessException("Only periodic scenarios can be scheduled"))
    }
  }

  private def cancelIfJobPresent(processVersion: ProcessVersion, user: User): Future[Unit] = {
    findJobStatus(processVersion.processName)
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

  override def test[T](name: ProcessName, json: String, testData: TestProcess.TestData, variableEncoder: Any => T): Future[TestProcess.TestResults[T]] =
    delegate.test(name, json, testData, variableEncoder)

  override def findJobStatus(name: ProcessName): Future[Option[ProcessState]] = {
    def createScheduledProcessState(processDeployment: PeriodicProcessDeployment): ProcessState = {
      ProcessState(
        Some(ExternalDeploymentId("future")),
        status = ScheduledStatus(processDeployment.runAt),
        version = Option(processDeployment.periodicProcess.processVersion),
        definitionManager = processStateDefinitionManager,
        //TODO: this date should be passed/handled through attributes
        startTime = Option(processDeployment.runAt.toEpochSecond(ZoneOffset.UTC)),
        attributes = Option.empty,
        errors = List.empty
      )
    }

    def createFailedProcessState(processDeployment: PeriodicProcessDeployment): ProcessState = {
      ProcessState(
        Some(ExternalDeploymentId("future")),
        status = SimpleStateStatus.Failed,
        version = Option(processDeployment.periodicProcess.processVersion),
        definitionManager = processStateDefinitionManager,
        startTime = Option.empty,
        attributes = Option.empty,
        errors = List.empty
      )
    }

    def handleFailed(original: Option[ProcessState]): Future[Option[ProcessState]] = {
      service.getLatestDeployment(name).map {
        // this method returns only active schedules, so 'None' means this process has been already canceled
        case None => original.map(_.copy(status = SimpleStateStatus.Canceled))
        // Previous, failed job is still accessible via Flink API but process has been scheduled to run again in future.
        case Some(processDeployment) if processDeployment.state.status == PeriodicProcessDeploymentStatus.Scheduled =>
          Some(createScheduledProcessState(processDeployment))
        case _ => original
      }
    }

    def handleScheduled(original: Option[ProcessState]): Future[Option[ProcessState]] = {
      service.getLatestDeployment(name).map { maybeProcessDeployment =>
        maybeProcessDeployment.map { processDeployment =>
          processDeployment.state.status match {
            case PeriodicProcessDeploymentStatus.Scheduled => Some(createScheduledProcessState(processDeployment))
            case PeriodicProcessDeploymentStatus.Failed => Some(createFailedProcessState(processDeployment))
            case PeriodicProcessDeploymentStatus.Deployed | PeriodicProcessDeploymentStatus.Finished =>
              original.map(o => o.copy(status = WaitingForScheduleStatus))
          }
        }.getOrElse(original)
      }
    }

    // Just to trigger periodic definition manager, e.g. override actions.
    def withPeriodicProcessState(state: ProcessState): ProcessState = ProcessState(
      deploymentId = state.deploymentId,
      status = state.status,
      version = state.version,
      definitionManager = processStateDefinitionManager,
      startTime = state.startTime,
      attributes = state.attributes,
      errors = state.errors
    )

    delegate
      .findJobStatus(name)
      .flatMap {
        // Scheduled again or waiting to be scheduled again.
        case state@Some(js) if js.status.isFinished => handleScheduled(state)
        case state@Some(js) if js.status.isFailed => handleFailed(state)
        // Job was previously canceled and it still exists on Flink but a new periodic job can be already scheduled.
        case state@Some(js) if js.status == SimpleStateStatus.Canceled => handleScheduled(state)
        // Scheduled or never started or latest run already disappeared in Flink.
        case state@None => handleScheduled(state)
        case Some(js) => Future.successful(Some(js))
      }.map(_.map(withPeriodicProcessState))
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

  override def customActions: List[CustomAction] = List.empty

  override def invokeCustomAction(actionRequest: CustomActionRequest,
                                  processDeploymentData: ProcessDeploymentData): Future[Either[CustomActionError, CustomActionResult]] =
    Future.successful(Left(CustomActionNotImplemented(actionRequest)))
}

case class ScheduledStatus(nextRunAt: LocalDateTime) extends CustomStateStatus("SCHEDULED") {
  override def isRunning: Boolean = true
}

case object WaitingForScheduleStatus extends CustomStateStatus("WAITING_FOR_SCHEDULE") {
  override def isRunning: Boolean = true
}
