package pl.touk.nussknacker.engine.management.periodic.model

import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.management.periodic.{ScheduleProperty, SingleScheduleProperty}
import slick.lifted.MappedTo

import java.time.LocalDateTime

case class PeriodicProcessId(value: Long) extends AnyVal with MappedTo[Long]

case class PeriodicProcess(id: PeriodicProcessId,
                           deploymentData: DeploymentWithJarData,
                           scheduleProperty: ScheduleProperty,
                           active: Boolean,
                           createdAt: LocalDateTime) {
  val processVersion: ProcessVersion = deploymentData.processVersion
}
