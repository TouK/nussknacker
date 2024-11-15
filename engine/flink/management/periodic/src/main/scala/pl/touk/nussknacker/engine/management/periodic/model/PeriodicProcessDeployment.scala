package pl.touk.nussknacker.engine.management.periodic.model

import pl.touk.nussknacker.engine.management.periodic.model.PeriodicProcessDeploymentStatus.PeriodicProcessDeploymentStatus
import pl.touk.nussknacker.engine.management.periodic.{MultipleScheduleProperty, SingleScheduleProperty}
import slick.lifted.MappedTo

import java.time.{Clock, ZonedDateTime}

// TODO: We should separate schedules concept from deployments - fully switch to ScheduleData and ScheduleDeploymentData
case class PeriodicProcessDeployment[ProcessRep](
    id: PeriodicProcessDeploymentId,
    periodicProcess: PeriodicProcess[ProcessRep],
    createdAt: ZonedDateTime,
    runAt: ZonedDateTime,
    scheduleName: ScheduleName,
    retriesLeft: Int,
    nextRetryAt: Option[ZonedDateTime],
    state: PeriodicProcessDeploymentState
) {

  def nextRunAt(clock: Clock): Either[String, Option[ZonedDateTime]] =
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
    deployedAt: Option[ZonedDateTime],
    completedAt: Option[ZonedDateTime],
    status: PeriodicProcessDeploymentStatus
)

case class PeriodicProcessDeploymentId(value: Long) extends AnyVal with MappedTo[Long] {
  override def toString: String = value.toString
}

object PeriodicProcessDeploymentStatus extends Enumeration {
  type PeriodicProcessDeploymentStatus = Value

  val Scheduled, Deployed, Finished, Failed, RetryingDeploy, FailedOnDeploy = Value
}

case class ScheduleName(value: Option[String]) {
  def display: String = value.getOrElse("[default]")
}
