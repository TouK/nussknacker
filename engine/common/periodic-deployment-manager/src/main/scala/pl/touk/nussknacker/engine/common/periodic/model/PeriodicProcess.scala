package pl.touk.nussknacker.engine.common.periodic.model

import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.deployment.ProcessActionId
import pl.touk.nussknacker.engine.common.periodic.ScheduleProperty

import java.time.LocalDateTime

case class PeriodicProcessId(value: Long)

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
