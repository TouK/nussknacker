package pl.touk.nussknacker.engine.management.periodic.model

import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.deployment.ProcessActionId
import pl.touk.nussknacker.engine.management.periodic.ScheduleProperty
import slick.lifted.MappedTo

import java.time.LocalDateTime

case class PeriodicProcessId(value: Long) extends MappedTo[Long]

case class PeriodicProcess[DeploymentData <: DeploymentWithJarData](
    id: PeriodicProcessId,
    deploymentData: DeploymentData,
    scheduleProperty: ScheduleProperty,
    active: Boolean,
    createdAt: LocalDateTime,
    processActionId: Option[ProcessActionId]
)
