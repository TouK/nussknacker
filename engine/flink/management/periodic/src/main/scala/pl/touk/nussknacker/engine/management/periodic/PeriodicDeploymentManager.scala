package pl.touk.nussknacker.engine.management.periodic

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.api.process.{ProcessIdWithName, ProcessName}
import pl.touk.nussknacker.engine.api.test.ScenarioTestData
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.deployment.{DeploymentData, DeploymentId, ExternalDeploymentId, User}
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

  override def validate(
      processVersion: ProcessVersion,
      deploymentData: DeploymentData,
      canonicalProcess: CanonicalProcess
  ): Future[Unit] = {
    for {
      scheduledProperty <- extractScheduleProperty(canonicalProcess)
      _                 <- Future.fromTry(service.prepareInitialScheduleDates(scheduledProperty).toTry)
      _                 <- delegate.validate(processVersion, deploymentData, canonicalProcess)
    } yield ()
  }

  override def deploy(
      processVersion: ProcessVersion,
      deploymentData: DeploymentData,
      canonicalProcess: CanonicalProcess,
      savepointPath: Option[String]
  ): Future[Option[ExternalDeploymentId]] = {
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
          cancel(processVersion.processName, deploymentData.user)
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

  override def stop(name: ProcessName, savepointDir: Option[String], user: User): Future[SavepointResult] = {
    service.deactivate(name).flatMap { deploymentIdsToStop =>
      // TODO: should return List of SavepointResult
      Future
        .sequence(deploymentIdsToStop.map(delegate.stop(name, _, savepointDir, user)))
        .map(_.headOption.getOrElse {
          throw new IllegalStateException(s"No running deployment for scenario: $name found")
        })
    }
  }

  override def stop(
      name: ProcessName,
      deploymentId: DeploymentId,
      savepointDir: Option[String],
      user: User
  ): Future[SavepointResult] =
    Future.failed(new UnsupportedOperationException(s"Stopping of deployment is not supported"))

  override def cancel(name: ProcessName, user: User): Future[Unit] = {
    service.deactivate(name).flatMap { deploymentIdsToCancel =>
      Future.sequence(deploymentIdsToCancel.map(delegate.cancel(name, _, user))).map(_ => ())
    }
  }

  override def cancel(name: ProcessName, deploymentId: DeploymentId, user: User): Future[Unit] =
    Future.failed(new UnsupportedOperationException(s"Cancelling of deployment it not supported"))

  override def test(
      name: ProcessName,
      canonicalProcess: CanonicalProcess,
      scenarioTestData: ScenarioTestData
  ): Future[TestProcess.TestResults] =
    delegate.test(name, canonicalProcess, scenarioTestData)

  override def getProcessStates(
      name: ProcessName
  )(implicit freshnessPolicy: DataFreshnessPolicy): Future[WithDataFreshnessStatus[List[StatusDetails]]] = {
    throw new IllegalAccessException(
      "PeriodicDeploymentManager.getProcessStates is not meant to be run directly - should be used getProcessState instead"
    )
  }

  override def getProcessState(idWithName: ProcessIdWithName, lastStateAction: Option[ProcessAction])(
      implicit freshnessPolicy: DataFreshnessPolicy
  ): Future[WithDataFreshnessStatus[ProcessState]] = {
    service.getStatusDetails(idWithName.name).map { statusesWithFreshness =>
      statusesWithFreshness.map { cd =>
        // TODO: add "real" presentation of deployments in GUI
        val mergedStatus = processStateDefinitionManager
          .processState(cd.copy(status = cd.status.asInstanceOf[PeriodicProcessStatus].mergedStatusDetails.status))
        mergedStatus.copy(tooltip = processStateDefinitionManager.statusTooltip(cd.status))
      }
    }
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

  override def invokeCustomAction(
      actionRequest: CustomActionRequest,
      canonicalProcess: CanonicalProcess
  ): Future[CustomActionResult] =
    customActionsProvider.invokeCustomAction(actionRequest, canonicalProcess)

}
