package pl.touk.nussknacker.engine.api.deployment.periodic.model

import pl.touk.nussknacker.engine.api.process.{ProcessName, VersionId}

import java.time.LocalDateTime

case class PeriodicProcessDeploymentDetails(
    id: Long,
    processName: ProcessName,
    versionId: VersionId,
    scheduleName: Option[String],
    createdAt: LocalDateTime,
    runAt: LocalDateTime,
    deployedAt: Option[LocalDateTime],
    completedAt: Option[LocalDateTime],
    status: PeriodicDeployStatus,
)
