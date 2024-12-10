package pl.touk.nussknacker.engine.management.periodic.model

import pl.touk.nussknacker.engine.api.deployment.ProcessActionId
import pl.touk.nussknacker.engine.api.process.{ProcessName, VersionId}
import pl.touk.nussknacker.engine.management.periodic.ScheduleProperty
import pl.touk.nussknacker.engine.management.periodic.db.{PeriodicProcessDeploymentEntity, PeriodicProcessesRepository}
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap

import java.time.LocalDateTime

// This class represents desired structure. We want to have deployments organized around schedules.
// Schedules should be separate concepts and should be possible to operate on them (e.g. deactivate)
// All deployments should be assigned to schedules via synthetic identifier.
// Because of historical reasons, we operate on PeriodicProcesses, schedule definition is nested in
// schedule_property and deployments are assigned to schedules by pair (process_id, schedule_name)
// TODO: refactor data structure and replace all usages of groupedByPeriodicProcess by direct usage of more linear schedules
case class SchedulesState(schedules: Map[ScheduleId, ScheduleData]) {

  def size: Int = schedules.size

  def isEmpty: Boolean = schedules.isEmpty

  def groupByProcessName: Map[ProcessName, SchedulesState] =
    schedules.groupBy(_._2.periodicProcessMetadata.processName).mapValuesNow(SchedulesState)

  lazy val groupedByPeriodicProcess: List[PeriodicProcessScheduleData] =
    schedules.toList.groupBy(_._2.periodicProcessMetadata).toList.map {
      case (periodicProcessMetadata, groupedSchedules) =>
        val deploymentsForSchedules = groupedSchedules.flatMap { case (scheduleId, scheduleData) =>
          scheduleData.latestDeployments.map(_.toFullDeploymentData(periodicProcessMetadata, scheduleId.scheduleName))
        }
        PeriodicProcessScheduleData(periodicProcessMetadata, deploymentsForSchedules)
    }

}

// This structure contains list of latest deployments of given schedule identified by processId and scheduleName
// For most operations it will contain only one latest deployment but for purpose of statuses of historical deployments
// it has list instead of one element.
// This structure should contain SingleScheduleProperty as well. See note above
case class ScheduleData(
    periodicProcessMetadata: PeriodicProcessMetadata,
    latestDeployments: List[ScheduleDeploymentData]
)

case class PeriodicProcessMetadata(
    id: PeriodicProcessId,
    processName: ProcessName,
    versionId: VersionId,
    jarFileName: String,
    scheduleProperty: ScheduleProperty,
    active: Boolean,
    processActionId: Option[ProcessActionId],
)

// To identify schedule we need scheduleName - None for SingleScheduleProperty and Some(key) for MultipleScheduleProperty keys
// Also we need PeriodicProcessId to distinguish between active schedules and some inactive from the past for the same PeriodicProcessId
// Warning: PeriodicProcessId is not the same as ProcessId - we match processes with schedules by process_name
case class ScheduleId(processId: PeriodicProcessId, scheduleName: ScheduleName)

case class ScheduleDeploymentData(
    id: PeriodicProcessDeploymentId,
    createdAt: LocalDateTime,
    runAt: LocalDateTime,
    deployedAt: Option[LocalDateTime],
    retriesLeft: Int,
    nextRetryAt: Option[LocalDateTime],
    state: PeriodicProcessDeploymentState
) {

  def toFullDeploymentData(
      periodicProcessMetadata: PeriodicProcessMetadata,
      scheduleName: ScheduleName
  ): PeriodicProcessDeployment =
    PeriodicProcessDeployment(
      id,
      periodicProcessMetadata,
      createdAt,
      runAt,
      scheduleName,
      retriesLeft,
      nextRetryAt,
      state
    )

  def display = s"deploymentId=$id"

}

object ScheduleDeploymentData {

  def apply(deployment: PeriodicProcessDeploymentEntity): ScheduleDeploymentData = {
    ScheduleDeploymentData(
      deployment.id,
      deployment.createdAt,
      deployment.runAt,
      deployment.deployedAt,
      deployment.retriesLeft,
      deployment.nextRetryAt,
      PeriodicProcessesRepository.createPeriodicDeploymentState(deployment)
    )
  }

}

// These below are temporary structures, see notice next to SchedulesState
case class PeriodicProcessScheduleData(
    periodicProcessMetadata: PeriodicProcessMetadata,
    deployments: List[PeriodicProcessDeployment]
) {
  def existsDeployment(predicate: PeriodicProcessDeployment => Boolean): Boolean = deployments.exists(predicate)

  def display: String = {
    val deploymentsForSchedules = deployments.map(_.display)
    s"processName=${periodicProcessMetadata.processName}, deploymentsForSchedules=$deploymentsForSchedules"
  }

}
