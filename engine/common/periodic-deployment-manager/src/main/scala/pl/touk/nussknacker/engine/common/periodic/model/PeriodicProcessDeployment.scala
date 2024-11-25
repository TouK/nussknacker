package pl.touk.nussknacker.engine.common.periodic.model

import pl.touk.nussknacker.engine.common.periodic.model.PeriodicProcessDeploymentStatus.PeriodicProcessDeploymentStatus
import pl.touk.nussknacker.engine.common.periodic.{MultipleScheduleProperty, SingleScheduleProperty}

import java.time.{Clock, LocalDateTime}

// TODO: We should separate schedules concept from deployments - fully switch to ScheduleData and ScheduleDeploymentData
case class PeriodicProcessDeployment[ProcessRep](
    id: PeriodicProcessDeploymentId,
    periodicProcess: PeriodicProcess[ProcessRep],
    createdAt: LocalDateTime,
    runAt: LocalDateTime,
    scheduleName: ScheduleName,
    retriesLeft: Int,
    nextRetryAt: Option[LocalDateTime],
    state: PeriodicProcessDeploymentState
) {

  def nextRunAt(clock: Clock): Either[String, Option[LocalDateTime]] =
    (periodicProcess.scheduleProperty, scheduleName.value) match {
      case (MultipleScheduleProperty(schedules), Some(name)) =>
        schedules.get(name).toRight(s"Failed to find schedule: $scheduleName").flatMap(_.nextRunAt(clock))
      case (e: SingleScheduleProperty, None) => e.nextRunAt(clock)
      case (schedule, name)                  => Left(s"Schedule name: $name mismatch with schedule: $schedule")
    }

  def display: String =
    s"${periodicProcess.processVersion} with scheduleName=${scheduleName.display} and deploymentId=$id"

}

case class PeriodicProcessDeploymentState(
    deployedAt: Option[LocalDateTime],
    completedAt: Option[LocalDateTime],
    status: PeriodicProcessDeploymentStatus
)

case class PeriodicProcessDeploymentId(value: Long) {
  override def toString: String = value.toString
}

object PeriodicProcessDeploymentStatus extends Enumeration {
  type PeriodicProcessDeploymentStatus = Value

  val Scheduled, Deployed, Finished, Failed, RetryingDeploy, FailedOnDeploy = Value
}

case class ScheduleName(value: Option[String]) {
  def display: String = value.getOrElse("[default]")
}
