package pl.touk.nussknacker.engine.api.deployment.periodic

import pl.touk.nussknacker.engine.api.deployment.ProcessActionId
import pl.touk.nussknacker.engine.api.deployment.periodic.PeriodicProcessesManager.ScheduleProperty
import pl.touk.nussknacker.engine.api.deployment.periodic.model.PeriodicProcessDeploymentStatus.PeriodicProcessDeploymentStatus
import pl.touk.nussknacker.engine.api.deployment.periodic.model._
import pl.touk.nussknacker.engine.api.process.{ProcessId, ProcessName, VersionId}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess

import java.time.LocalDateTime
import scala.concurrent.Future

trait PeriodicProcessesManager {

  def create(
      deploymentWithRuntimeParams: DeploymentWithRuntimeParams,
      scheduleProperty: ScheduleProperty,
      processActionId: ProcessActionId,
      processingType: String,
  ): Future[PeriodicProcess]

  def markInactive(processId: PeriodicProcessId): Future[Unit]

  def schedule(
      id: PeriodicProcessId,
      scheduleName: ScheduleName,
      runAt: LocalDateTime,
      deployMaxRetries: Int,
  ): Future[PeriodicProcessDeployment]

  def findProcessData(id: PeriodicProcessDeploymentId, processingType: String): Future[PeriodicProcessDeployment]

  def findToBeDeployed(processingType: String): Future[Seq[PeriodicProcessDeployment]]

  def findToBeRetried(processingType: String): Future[Seq[PeriodicProcessDeployment]]

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
      scenarioName: ProcessName
  ): Future[SchedulesState]

  def getLatestDeploymentsForActiveSchedules(
      processName: ProcessName,
      deploymentsPerScheduleMaxCount: Int,
      processingType: String,
  ): Future[SchedulesState]

  def getLatestDeploymentsForLatestInactiveSchedules(
      processName: ProcessName,
      inactiveProcessesMaxCount: Int,
      deploymentsPerScheduleMaxCount: Int,
      processingType: String
  ): Future[SchedulesState]

  def findActiveSchedulesForProcessesHavingDeploymentWithMatchingStatus(
      expectedDeploymentStatuses: Set[PeriodicProcessDeploymentStatus],
      processingType: String,
  ): Future[SchedulesState]

  def fetchCanonicalProcess(
      processId: ProcessId,
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
      deploymentWithRuntimeParams: DeploymentWithRuntimeParams,
      scheduleProperty: ScheduleProperty,
      processActionId: ProcessActionId,
      processingType: String,
  ): Future[PeriodicProcess] = notImplemented

  override def markInactive(processId: PeriodicProcessId): Future[Unit] = notImplemented

  override def schedule(
      id: PeriodicProcessId,
      scheduleName: ScheduleName,
      runAt: LocalDateTime,
      deployMaxRetries: Int
  ): Future[PeriodicProcessDeployment] = notImplemented

  override def findProcessData(
      id: PeriodicProcessDeploymentId,
      processingType: String
  ): Future[PeriodicProcessDeployment] = notImplemented

  override def findToBeDeployed(processingType: String): Future[Seq[PeriodicProcessDeployment]] = notImplemented

  override def findToBeRetried(processingType: String): Future[Seq[PeriodicProcessDeployment]] = notImplemented

  override def markDeployed(id: PeriodicProcessDeploymentId): Future[Unit] = notImplemented

  override def markFinished(id: PeriodicProcessDeploymentId): Future[Unit] = notImplemented

  override def markFailed(id: PeriodicProcessDeploymentId): Future[Unit] = notImplemented

  override def markFailedOnDeployWithStatus(
      id: PeriodicProcessDeploymentId,
      status: PeriodicProcessDeploymentStatus,
      deployRetries: Int,
      retryAt: Option[LocalDateTime]
  ): Future[Unit] = notImplemented

  override def getSchedulesState(scenarioName: ProcessName): Future[SchedulesState] = notImplemented

  override def getLatestDeploymentsForActiveSchedules(
      processName: ProcessName,
      deploymentsPerScheduleMaxCount: Int,
      processingType: String,
  ): Future[SchedulesState] = notImplemented

  override def getLatestDeploymentsForLatestInactiveSchedules(
      processName: ProcessName,
      inactiveProcessesMaxCount: Int,
      deploymentsPerScheduleMaxCount: Int,
      processingType: String
  ): Future[SchedulesState] = notImplemented

  override def findActiveSchedulesForProcessesHavingDeploymentWithMatchingStatus(
      expectedDeploymentStatuses: Set[PeriodicProcessDeploymentStatus],
      processingType: String,
  ): Future[SchedulesState] = notImplemented

  override def fetchCanonicalProcess(
      processId: ProcessId,
      versionId: VersionId
  ): Future[Option[CanonicalProcess]] = notImplemented

  private def notImplemented: Future[Nothing] =
    Future.failed(new NotImplementedError())

}
