package pl.touk.nussknacker.ui.process.periodic

import com.typesafe.config.Config
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.deployment.ProcessActionId
import pl.touk.nussknacker.engine.api.deployment.periodic.model.DeploymentWithRuntimeParams
import pl.touk.nussknacker.engine.api.process.{ProcessName, VersionId}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.ui.db.DbRef
import pl.touk.nussknacker.ui.process.periodic.legacy.db.{LegacyDbInitializer, SlickLegacyPeriodicProcessesRepository}
import pl.touk.nussknacker.ui.process.periodic.model.PeriodicProcessDeploymentStatus.PeriodicProcessDeploymentStatus
import pl.touk.nussknacker.ui.process.periodic.model._
import pl.touk.nussknacker.ui.process.repository._
import pl.touk.nussknacker.ui.security.api.{AdminUser, NussknackerInternalUser}
import slick.jdbc
import slick.jdbc.JdbcProfile

import java.time.{Clock, LocalDateTime}
import scala.concurrent.{ExecutionContext, Future}

class RepositoryBasedPeriodicProcessesManagerProvider(dbRef: DbRef)(
    implicit executionContext: ExecutionContext
) extends PeriodicProcessesManagerProvider {

  override def provide(
      processingType: String,
      customLegacyDbConfig: Option[Config],
  ): PeriodicProcessesManager = {
    val clock = Clock.systemDefaultZone()
    val actionRepository =
      DbScenarioActionReadOnlyRepository.create(dbRef)
    val scenarioLabelsRepository =
      new ScenarioLabelsRepository(dbRef)
    val processRepository =
      DBFetchingProcessRepository.createFutureRepository(dbRef, actionRepository, scenarioLabelsRepository)
    val periodicProcessesRepository = customLegacyDbConfig match {
      case None =>
        new SlickPeriodicProcessesRepository(dbRef.db, dbRef.profile, clock)
      case Some(customDbConfig) =>
        val (db: jdbc.JdbcBackend.DatabaseDef, dbProfile: JdbcProfile) = LegacyDbInitializer.init(customDbConfig)
        new SlickLegacyPeriodicProcessesRepository(db, dbProfile, clock)
    }
    new RepositoryBasedPeriodicProcessesManager(
      processingType,
      periodicProcessesRepository,
      processRepository,
    )
  }

}

class RepositoryBasedPeriodicProcessesManager(
    processingType: String,
    periodicProcessesRepository: PeriodicProcessesRepository,
    fetchingProcessRepository: FetchingProcessRepository[Future]
) extends PeriodicProcessesManager {

  import periodicProcessesRepository._

  override def create(
      deploymentWithRuntimeParams: DeploymentWithRuntimeParams,
      inputConfigDuringExecutionJson: String,
      canonicalProcess: CanonicalProcess,
      scheduleProperty: ScheduleProperty,
      processActionId: ProcessActionId,
  ): Future[PeriodicProcess] =
    periodicProcessesRepository
      .create(
        deploymentWithRuntimeParams,
        inputConfigDuringExecutionJson,
        canonicalProcess,
        scheduleProperty,
        processActionId,
        processingType
      )
      .run

  override def markInactive(processId: PeriodicProcessId): Future[Unit] =
    periodicProcessesRepository.markInactive(processId).run

  override def schedule(
      id: PeriodicProcessId,
      scheduleName: ScheduleName,
      runAt: LocalDateTime,
      deployMaxRetries: Int
  ): Future[PeriodicProcessDeployment] =
    periodicProcessesRepository.schedule(id, scheduleName, runAt, deployMaxRetries).run

  override def findProcessData(
      id: PeriodicProcessDeploymentId,
  ): Future[PeriodicProcessDeployment] =
    periodicProcessesRepository.findProcessData(id).run

  override def findToBeDeployed: Future[Seq[PeriodicProcessDeployment]] =
    periodicProcessesRepository.findToBeDeployed(processingType).run

  override def findToBeRetried: Future[Seq[PeriodicProcessDeployment]] =
    periodicProcessesRepository.findToBeRetried(processingType).run

  override def markDeployed(id: PeriodicProcessDeploymentId): Future[Unit] =
    periodicProcessesRepository.markDeployed(id).run

  override def markFinished(id: PeriodicProcessDeploymentId): Future[Unit] =
    periodicProcessesRepository.markFinished(id).run

  override def markFailed(id: PeriodicProcessDeploymentId): Future[Unit] =
    periodicProcessesRepository.markFailed(id).run

  override def markFailedOnDeployWithStatus(
      id: PeriodicProcessDeploymentId,
      status: PeriodicProcessDeploymentStatus,
      deployRetries: Int,
      retryAt: Option[LocalDateTime]
  ): Future[Unit] = periodicProcessesRepository.markFailedOnDeployWithStatus(id, status, deployRetries, retryAt).run

  override def getSchedulesState(scenarioName: ProcessName, after: Option[LocalDateTime]): Future[SchedulesState] =
    periodicProcessesRepository.getSchedulesState(scenarioName, after).run

  override def getLatestDeploymentsForActiveSchedules(
      processName: ProcessName,
      deploymentsPerScheduleMaxCount: Int,
  ): Future[SchedulesState] =
    periodicProcessesRepository
      .getLatestDeploymentsForActiveSchedules(processName, deploymentsPerScheduleMaxCount, processingType)
      .run

  override def getLatestDeploymentsForActiveSchedules(
      deploymentsPerScheduleMaxCount: Int,
  ): Future[Map[ProcessName, SchedulesState]] =
    periodicProcessesRepository
      .getLatestDeploymentsForActiveSchedules(deploymentsPerScheduleMaxCount, processingType)
      .run

  override def getLatestDeploymentsForLatestInactiveSchedules(
      processName: ProcessName,
      inactiveProcessesMaxCount: Int,
      deploymentsPerScheduleMaxCount: Int,
  ): Future[SchedulesState] =
    periodicProcessesRepository
      .getLatestDeploymentsForLatestInactiveSchedules(
        processName,
        inactiveProcessesMaxCount,
        deploymentsPerScheduleMaxCount,
        processingType,
      )
      .run

  override def getLatestDeploymentsForLatestInactiveSchedules(
      inactiveProcessesMaxCount: Int,
      deploymentsPerScheduleMaxCount: Int,
  ): Future[Map[ProcessName, SchedulesState]] =
    periodicProcessesRepository
      .getLatestDeploymentsForLatestInactiveSchedules(
        inactiveProcessesMaxCount,
        deploymentsPerScheduleMaxCount,
        processingType,
      )
      .run

  override def findActiveSchedulesForProcessesHavingDeploymentWithMatchingStatus(
      expectedDeploymentStatuses: Set[PeriodicProcessDeploymentStatus],
  ): Future[SchedulesState] = periodicProcessesRepository
    .findActiveSchedulesForProcessesHavingDeploymentWithMatchingStatus(expectedDeploymentStatuses, processingType)
    .run

  override def fetchCanonicalProcessWithVersion(
      processName: ProcessName,
      versionId: VersionId
  ): Future[Option[(CanonicalProcess, ProcessVersion)]] = {
    implicit val user: AdminUser = NussknackerInternalUser.instance
    fetchingProcessRepository.getCanonicalProcessWithVersion(processName, versionId)
  }

  override def fetchInputConfigDuringExecutionJson(
      processName: ProcessName,
      versionId: VersionId
  ): Future[Option[String]] =
    periodicProcessesRepository.fetchInputConfigDuringExecutionJson(processName, versionId).run

}
