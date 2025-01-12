package pl.touk.nussknacker.engine.api.deployment.periodic

import pl.touk.nussknacker.engine.api.process.{ProcessName, VersionId}
import pl.touk.nussknacker.engine.api.{MetaData, ProcessVersion}

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

case class PeriodicProcessDetails(
    processVersion: ProcessVersion,
    processMetaData: MetaData,
    inputConfigDuringExecutionJson: String,
)

sealed trait PeriodicDeployStatus

object PeriodicDeployStatus {
  case object Scheduled      extends PeriodicDeployStatus
  case object Deployed       extends PeriodicDeployStatus
  case object Finished       extends PeriodicDeployStatus
  case object Failed         extends PeriodicDeployStatus
  case object RetryingDeploy extends PeriodicDeployStatus
  case object FailedOnDeploy extends PeriodicDeployStatus
}
