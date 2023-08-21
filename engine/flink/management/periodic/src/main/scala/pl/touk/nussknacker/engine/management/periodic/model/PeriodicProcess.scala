package pl.touk.nussknacker.engine.management.periodic.model

import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.management.periodic.{MultipleScheduleProperty, ScheduleProperty, SingleScheduleProperty}
import slick.lifted.MappedTo

import java.time.{Clock, LocalDateTime}

case class PeriodicProcessId(value: Long) extends MappedTo[Long]

case class PeriodicProcess(id: PeriodicProcessId,
                           deploymentData: DeploymentWithJarData,
                           scheduleProperty: ScheduleProperty,
                           active: Boolean,
                           createdAt: LocalDateTime) {
  val processVersion: ProcessVersion = deploymentData.processVersion

  def nextRunAt(clock: Clock, scheduleName: ScheduleName): Either[String, Option[LocalDateTime]] = (scheduleProperty, scheduleName.value) match {
    case (MultipleScheduleProperty(schedules), Some(name)) =>
      schedules.get(name).toRight(s"Failed to find schedule: ${scheduleName.display}").flatMap(_.nextRunAt(clock))
    case (e: SingleScheduleProperty, None) => e.nextRunAt(clock)
    case (schedule, name) => Left(s"Schedule name: $name mismatch with schedule: $schedule")
  }

}
