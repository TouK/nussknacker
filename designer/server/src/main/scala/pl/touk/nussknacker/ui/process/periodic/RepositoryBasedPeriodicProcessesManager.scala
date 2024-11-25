package pl.touk.nussknacker.ui.process.periodic

import pl.touk.nussknacker.engine.api.deployment.ProcessActionId
import pl.touk.nussknacker.engine.api.deployment.periodic.PeriodicProcessesManager
import pl.touk.nussknacker.engine.api.deployment.periodic.model.PeriodicProcessDeploymentStatus.PeriodicProcessDeploymentStatus
import pl.touk.nussknacker.engine.api.deployment.periodic.model._
import pl.touk.nussknacker.engine.api.process.{ProcessId, ProcessName, VersionId}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.ui.process.repository.PeriodicProcessesRepository

import java.time.LocalDateTime
import scala.concurrent.Future

class RepositoryBasedPeriodicProcessesManager(
    periodicProcessesRepository: PeriodicProcessesRepository,
) extends PeriodicProcessesManager {

  import periodicProcessesRepository._

  override def create(
      deploymentWithRuntimeParams: DeploymentWithRuntimeParams,
      scheduleProperty: PeriodicProcessesManager.ScheduleProperty,
      processActionId: ProcessActionId,
      processingType: String,
  ): Future[PeriodicProcess] =
    periodicProcessesRepository
      .create(deploymentWithRuntimeParams, scheduleProperty, processActionId, processingType)
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
      processingType: String
  ): Future[PeriodicProcessDeployment] =
    periodicProcessesRepository.findProcessData(id).run

  override def findToBeDeployed(processingType: String): Future[Seq[PeriodicProcessDeployment]] =
    periodicProcessesRepository.findToBeDeployed(processingType).run

  override def findToBeRetried(processingType: String): Future[Seq[PeriodicProcessDeployment]] =
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

  override def getSchedulesState(scenarioName: ProcessName): Future[SchedulesState] =
    periodicProcessesRepository.getSchedulesState(scenarioName).run

  override def getLatestDeploymentsForActiveSchedules(
      processName: ProcessName,
      deploymentsPerScheduleMaxCount: Int,
      processingType: String,
  ): Future[SchedulesState] =
    periodicProcessesRepository
      .getLatestDeploymentsForActiveSchedules(processName, deploymentsPerScheduleMaxCount, processingType)
      .run

  override def getLatestDeploymentsForLatestInactiveSchedules(
      processName: ProcessName,
      inactiveProcessesMaxCount: Int,
      deploymentsPerScheduleMaxCount: Int,
      processingType: String,
  ): Future[SchedulesState] =
    periodicProcessesRepository
      .getLatestDeploymentsForLatestInactiveSchedules(
        processName,
        inactiveProcessesMaxCount,
        deploymentsPerScheduleMaxCount,
        processingType,
      )
      .run

  override def findActiveSchedulesForProcessesHavingDeploymentWithMatchingStatus(
      expectedDeploymentStatuses: Set[PeriodicProcessDeploymentStatus],
      processingType: String,
  ): Future[SchedulesState] = periodicProcessesRepository
    .findActiveSchedulesForProcessesHavingDeploymentWithMatchingStatus(expectedDeploymentStatuses, processingType)
    .run

  override def fetchCanonicalProcess(
      processId: ProcessId,
      versionId: VersionId
  ): Future[Option[CanonicalProcess]] =
    periodicProcessesRepository.fetchCanonicalProcess(processId, versionId).run

}
