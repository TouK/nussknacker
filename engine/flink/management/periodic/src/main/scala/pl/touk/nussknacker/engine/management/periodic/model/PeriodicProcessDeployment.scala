package pl.touk.nussknacker.engine.management.periodic.model

import pl.touk.nussknacker.engine.management.periodic.{MultipleScheduleProperty, SingleScheduleProperty}
import pl.touk.nussknacker.engine.management.periodic.model.PeriodicProcessDeploymentStatus.PeriodicProcessDeploymentStatus
import slick.lifted.MappedTo

import java.time.{Clock, LocalDateTime}

case class PeriodicProcessDeployment(id: PeriodicProcessDeploymentId,
                                     periodicProcess: PeriodicProcess,
                                     runAt: LocalDateTime,
                                     scheduleName: Option[String],
                                     retriesLeft: Int,
                                     nextRetryAt: Option[LocalDateTime],
                                     state: PeriodicProcessDeploymentState) {

  def nextRunAt(clock: Clock): Either[String, Option[LocalDateTime]] = (periodicProcess.scheduleProperty, scheduleName) match {
    case (MultipleScheduleProperty(schedules), Some(name)) =>
      schedules.get(name).toRight(s"Failed to find schedule: $scheduleName").right.flatMap(_.nextRunAt(clock))
    case (e:SingleScheduleProperty, None) => e.nextRunAt(clock)
    case (schedule, name) => Left(s"Schedule name: $name mismatch with schedule: $schedule")
  }

}

case class PeriodicProcessDeploymentState(deployedAt: Option[LocalDateTime],
                                     completedAt: Option[LocalDateTime],
                                     status: PeriodicProcessDeploymentStatus)

case class PeriodicProcessDeploymentId(value: Long) extends AnyVal with MappedTo[Long]

object PeriodicProcessDeploymentStatus extends Enumeration {
  type PeriodicProcessDeploymentStatus = Value

  val Scheduled, Deployed, Finished, Failed, FailedOnDeploy = Value
}
