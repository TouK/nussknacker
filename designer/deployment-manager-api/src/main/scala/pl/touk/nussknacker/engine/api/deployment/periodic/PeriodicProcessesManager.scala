package pl.touk.nussknacker.engine.api.deployment.periodic

import pl.touk.nussknacker.engine.api.ProcessVersion
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
      deploymentWithRuntimeParams: DeploymentWithRuntimeParams,
      inputConfigDuringExecutionJson: String,
      canonicalProcess: CanonicalProcess,
      scheduleProperty: ScheduleProperty,
      processActionId: ProcessActionId,
  ): Future[PeriodicProcess]

  def markInactive(processId: PeriodicProcessId): Future[Unit]

  def schedule(
      id: PeriodicProcessId,
      scheduleName: ScheduleName,
      runAt: LocalDateTime,
      deployMaxRetries: Int,
  ): Future[PeriodicProcessDeployment]

  def findProcessData(
      id: PeriodicProcessDeploymentId
  ): Future[PeriodicProcessDeployment]

  def findToBeDeployed: Future[Seq[PeriodicProcessDeployment]]

  def findToBeRetried: Future[Seq[PeriodicProcessDeployment]]

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

  def getLatestDeploymentsForActiveSchedules(
      deploymentsPerScheduleMaxCount: Int,
  ): Future[Map[ProcessName, SchedulesState]]

  def getLatestDeploymentsForLatestInactiveSchedules(
      processName: ProcessName,
      inactiveProcessesMaxCount: Int,
      deploymentsPerScheduleMaxCount: Int,
  ): Future[SchedulesState]

  def getLatestDeploymentsForLatestInactiveSchedules(
      inactiveProcessesMaxCount: Int,
      deploymentsPerScheduleMaxCount: Int,
  ): Future[Map[ProcessName, SchedulesState]]

  def findActiveSchedulesForProcessesHavingDeploymentWithMatchingStatus(
      expectedDeploymentStatuses: Set[PeriodicProcessDeploymentStatus],
  ): Future[SchedulesState]

  def fetchCanonicalProcessWithVersion(
      processName: ProcessName,
      versionId: VersionId
  ): Future[Option[(CanonicalProcess, ProcessVersion)]]

  def fetchInputConfigDuringExecutionJson(
      processName: ProcessName,
      versionId: VersionId,
  ): Future[Option[String]]

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
      inputConfigDuringExecutionJson: String,
      canonicalProcess: CanonicalProcess,
      scheduleProperty: ScheduleProperty,
      processActionId: ProcessActionId,
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
  ): Future[PeriodicProcessDeployment] = notImplemented

  override def findToBeDeployed: Future[Seq[PeriodicProcessDeployment]] =
    notImplemented

  override def findToBeRetried: Future[Seq[PeriodicProcessDeployment]] =
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

  override def getLatestDeploymentsForActiveSchedules(
      deploymentsPerScheduleMaxCount: Int
  ): Future[Map[ProcessName, SchedulesState]] = notImplemented

  override def getLatestDeploymentsForLatestInactiveSchedules(
      processName: ProcessName,
      inactiveProcessesMaxCount: Int,
      deploymentsPerScheduleMaxCount: Int,
  ): Future[SchedulesState] = notImplemented

  override def getLatestDeploymentsForLatestInactiveSchedules(
      inactiveProcessesMaxCount: Int,
      deploymentsPerScheduleMaxCount: Int
  ): Future[Map[ProcessName, SchedulesState]] = notImplemented

  override def findActiveSchedulesForProcessesHavingDeploymentWithMatchingStatus(
      expectedDeploymentStatuses: Set[PeriodicProcessDeploymentStatus],
  ): Future[SchedulesState] = notImplemented

  override def fetchCanonicalProcessWithVersion(
      processName: ProcessName,
      versionId: VersionId
  ): Future[Option[(CanonicalProcess, ProcessVersion)]] = notImplemented

  override def fetchInputConfigDuringExecutionJson(
      processName: ProcessName,
      versionId: VersionId
  ): Future[Option[String]] = notImplemented

  private def notImplemented: Future[Nothing] =
    Future.failed(new NotImplementedError())

}