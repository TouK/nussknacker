package pl.touk.nussknacker.engine.api.deployment.periodic

import pl.touk.nussknacker.engine.api.deployment.ProcessActionId
import pl.touk.nussknacker.engine.api.deployment.periodic.PeriodicProcessesManager.ScheduleProperty
import pl.touk.nussknacker.engine.api.deployment.periodic.model.PeriodicProcessDeploymentStatus.PeriodicProcessDeploymentStatus
import pl.touk.nussknacker.engine.api.deployment.periodic.model._
import pl.touk.nussknacker.engine.api.process.{ProcessName, VersionId}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess

import java.time.LocalDateTime
import scala.concurrent.Future

trait PeriodicProcessesManager {

  def create(
      deploymentWithRuntimeParams: DeploymentWithRuntimeParams.WithConfig,
      scheduleProperty: ScheduleProperty,
      processActionId: ProcessActionId,
  ): Future[PeriodicProcess[DeploymentWithRuntimeParams.WithConfig]]

  def markInactive(processId: PeriodicProcessId): Future[Unit]

  def schedule(
      id: PeriodicProcessId,
      scheduleName: ScheduleName,
      runAt: LocalDateTime,
      deployMaxRetries: Int,
  ): Future[PeriodicProcessDeployment[DeploymentWithRuntimeParams.WithConfig]]

  def findProcessData(
      id: PeriodicProcessDeploymentId
  ): Future[PeriodicProcessDeployment[DeploymentWithRuntimeParams.WithConfig]]

  def findToBeDeployed: Future[Seq[PeriodicProcessDeployment[DeploymentWithRuntimeParams.WithConfig]]]

  def findToBeRetried: Future[Seq[PeriodicProcessDeployment[DeploymentWithRuntimeParams.WithConfig]]]

  def markDeployed(id: PeriodicProcessDeploymentId): Future[Unit]

  def markFinished(id: PeriodicProcessDeploymentId): Future[Unit]

  def markFailed(id: PeriodicProcessDeploymentId): Future[Unit]

  def markFailedOnDeployWithStatus(
      id: PeriodicProcessDeploymentId,
      status: PeriodicProcessDeploymentStatus,
      deployRetries: Int,
      retryAt: Option[LocalDateTime]
  ): Future[Unit]

  def getSchedulesState(
      scenarioName: ProcessName,
      after: Option[LocalDateTime],
  ): Future[SchedulesState]

  def getLatestDeploymentsForActiveSchedules(
      processName: ProcessName,
      deploymentsPerScheduleMaxCount: Int,
  ): Future[SchedulesState]

  def getLatestDeploymentsForLatestInactiveSchedules(
      processName: ProcessName,
      inactiveProcessesMaxCount: Int,
      deploymentsPerScheduleMaxCount: Int,
  ): Future[SchedulesState]

  def findActiveSchedulesForProcessesHavingDeploymentWithMatchingStatus(
      expectedDeploymentStatuses: Set[PeriodicProcessDeploymentStatus],
  ): Future[SchedulesState]

  def fetchCanonicalProcess(
      processName: ProcessName,
      versionId: VersionId,
  ): Future[Option[CanonicalProcess]]

}

object PeriodicProcessesManager {

  sealed trait ScheduleProperty

  sealed trait SingleScheduleProperty extends ScheduleProperty

  case class MultipleScheduleProperty(schedules: Map[String, SingleScheduleProperty]) extends ScheduleProperty

  case class CronScheduleProperty(labelOrCronExpr: String) extends SingleScheduleProperty

}

object NoOpPeriodicProcessesManager extends PeriodicProcessesManager {

  override def create(
      deploymentWithRuntimeParams: DeploymentWithRuntimeParams.WithConfig,
      scheduleProperty: ScheduleProperty,
      processActionId: ProcessActionId,
  ): Future[PeriodicProcess[DeploymentWithRuntimeParams.WithConfig]] = notImplemented

  override def markInactive(processId: PeriodicProcessId): Future[Unit] = notImplemented

  override def schedule(
      id: PeriodicProcessId,
      scheduleName: ScheduleName,
      runAt: LocalDateTime,
      deployMaxRetries: Int
  ): Future[PeriodicProcessDeployment[DeploymentWithRuntimeParams.WithConfig]] = notImplemented

  override def findProcessData(
      id: PeriodicProcessDeploymentId,
  ): Future[PeriodicProcessDeployment[DeploymentWithRuntimeParams.WithConfig]] = notImplemented

  override def findToBeDeployed: Future[Seq[PeriodicProcessDeployment[DeploymentWithRuntimeParams.WithConfig]]] =
    notImplemented

  override def findToBeRetried: Future[Seq[PeriodicProcessDeployment[DeploymentWithRuntimeParams.WithConfig]]] =
    notImplemented

  override def markDeployed(id: PeriodicProcessDeploymentId): Future[Unit] = notImplemented

  override def markFinished(id: PeriodicProcessDeploymentId): Future[Unit] = notImplemented

  override def markFailed(id: PeriodicProcessDeploymentId): Future[Unit] = notImplemented

  override def markFailedOnDeployWithStatus(
      id: PeriodicProcessDeploymentId,
      status: PeriodicProcessDeploymentStatus,
      deployRetries: Int,
      retryAt: Option[LocalDateTime]
  ): Future[Unit] = notImplemented

  override def getSchedulesState(scenarioName: ProcessName, after: Option[LocalDateTime]): Future[SchedulesState] =
    notImplemented

  override def getLatestDeploymentsForActiveSchedules(
      processName: ProcessName,
      deploymentsPerScheduleMaxCount: Int,
  ): Future[SchedulesState] = notImplemented

  override def getLatestDeploymentsForLatestInactiveSchedules(
      processName: ProcessName,
      inactiveProcessesMaxCount: Int,
      deploymentsPerScheduleMaxCount: Int,
  ): Future[SchedulesState] = notImplemented

  override def findActiveSchedulesForProcessesHavingDeploymentWithMatchingStatus(
      expectedDeploymentStatuses: Set[PeriodicProcessDeploymentStatus],
  ): Future[SchedulesState] = notImplemented

  override def fetchCanonicalProcess(
      processName: ProcessName,
      versionId: VersionId
  ): Future[Option[CanonicalProcess]] = notImplemented

  private def notImplemented: Future[Nothing] =
    Future.failed(new NotImplementedError())

}
