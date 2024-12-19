package pl.touk.nussknacker.ui.process.periodic

import pl.touk.nussknacker.engine.api.deployment.ProcessActionId
import pl.touk.nussknacker.engine.api.deployment.periodic.PeriodicProcessesManager
import pl.touk.nussknacker.engine.api.deployment.periodic.model.DeploymentWithRuntimeParams.WithConfig
import pl.touk.nussknacker.engine.api.deployment.periodic.model.PeriodicProcessDeploymentStatus.PeriodicProcessDeploymentStatus
import pl.touk.nussknacker.engine.api.deployment.periodic.model._
import pl.touk.nussknacker.engine.api.process.{ProcessId, ProcessName, VersionId}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.ui.process.repository.PeriodicProcessesRepository

import java.time.LocalDateTime
import scala.concurrent.Future

class RepositoryBasedPeriodicProcessesManager(
    deploymentManagerName: String,
    processingType: String,
    periodicProcessesRepository: PeriodicProcessesRepository,
) extends PeriodicProcessesManager {

  import periodicProcessesRepository._

  override def create(
      deploymentWithRuntimeParams: DeploymentWithRuntimeParams.WithConfig,
      scheduleProperty: PeriodicProcessesManager.ScheduleProperty,
      processActionId: ProcessActionId,
  ): Future[PeriodicProcess[DeploymentWithRuntimeParams.WithConfig]] =
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
  ): Future[PeriodicProcessDeployment[WithConfig]] =
    periodicProcessesRepository.schedule(id, scheduleName, runAt, deployMaxRetries).run

  override def findProcessData(
      id: PeriodicProcessDeploymentId,
  ): Future[PeriodicProcessDeployment[WithConfig]] =
    periodicProcessesRepository.findProcessData(id).run

  override def findToBeDeployed: Future[Seq[PeriodicProcessDeployment[WithConfig]]] =
    periodicProcessesRepository.findToBeDeployed(processingType).run

  override def findToBeRetried: Future[Seq[PeriodicProcessDeployment[WithConfig]]] =
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

  override def findActiveSchedulesForProcessesHavingDeploymentWithMatchingStatus(
      expectedDeploymentStatuses: Set[PeriodicProcessDeploymentStatus],
  ): Future[SchedulesState] = periodicProcessesRepository
    .findActiveSchedulesForProcessesHavingDeploymentWithMatchingStatus(expectedDeploymentStatuses, processingType)
    .run

  override def fetchCanonicalProcess(
      processName: ProcessName,
      versionId: VersionId
  ): Future[Option[CanonicalProcess]] =
    periodicProcessesRepository.fetchCanonicalProcess(processName, versionId).run

}
