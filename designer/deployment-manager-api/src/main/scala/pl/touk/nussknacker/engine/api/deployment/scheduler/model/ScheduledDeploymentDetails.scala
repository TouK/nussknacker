package pl.touk.nussknacker.engine.api.deployment.scheduler.model

import pl.touk.nussknacker.engine.api.process.{ProcessName, VersionId}

import java.time.LocalDateTime

case class ScheduledDeploymentDetails(
    id: Long,
    processName: ProcessName,
    versionId: VersionId,
    scheduleName: Option[String],
    createdAt: LocalDateTime,
    runAt: LocalDateTime,
    deployedAt: Option[LocalDateTime],
    completedAt: Option[LocalDateTime],
    status: ScheduledDeploymentStatus,
)

sealed trait ScheduledDeploymentStatus

object ScheduledDeploymentStatus {
  case object Scheduled      extends ScheduledDeploymentStatus
  case object Deployed       extends ScheduledDeploymentStatus
  case object Finished       extends ScheduledDeploymentStatus
  case object Failed         extends ScheduledDeploymentStatus
  case object RetryingDeploy extends ScheduledDeploymentStatus
  case object FailedOnDeploy extends ScheduledDeploymentStatus
}
