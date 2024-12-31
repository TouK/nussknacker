package pl.touk.nussknacker.engine.api.deployment.periodic.model

import pl.touk.nussknacker.engine.api.deployment.ProcessActionId
import pl.touk.nussknacker.engine.api.deployment.periodic.PeriodicProcessesManager.ScheduleProperty

import java.time.LocalDateTime

case class PeriodicProcessId(value: Long)

case class PeriodicProcess(
    id: PeriodicProcessId,
    deploymentData: DeploymentWithRuntimeParams,
    scheduleProperty: ScheduleProperty,
    active: Boolean,
    createdAt: LocalDateTime,
    processActionId: Option[ProcessActionId]
)