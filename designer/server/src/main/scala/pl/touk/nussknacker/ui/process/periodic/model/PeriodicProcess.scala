package pl.touk.nussknacker.ui.process.periodic.model

import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.deployment.ProcessActionId
import pl.touk.nussknacker.engine.periodic.DeploymentWithRuntimeParams
import pl.touk.nussknacker.ui.process.periodic.ScheduleProperty
import slick.lifted.MappedTo

import java.time.LocalDateTime

case class PeriodicProcessId(value: Long) extends MappedTo[Long]

case class PeriodicProcess[ProcessRep](
    id: PeriodicProcessId,
    deploymentData: DeploymentWithRuntimeParams[ProcessRep],
    scheduleProperty: ScheduleProperty,
    active: Boolean,
    createdAt: LocalDateTime,
    processActionId: Option[ProcessActionId]
) {
  val processVersion: ProcessVersion = deploymentData.processVersion
}
