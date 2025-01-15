package pl.touk.nussknacker.ui.process.periodic.model

import pl.touk.nussknacker.engine.api.deployment.scheduler.model.{ScheduledDeploymentDetails, ScheduledDeploymentStatus}
import pl.touk.nussknacker.ui.process.periodic.model.PeriodicProcessDeploymentStatus.{
  Deployed,
  Failed,
  FailedOnDeploy,
  Finished,
  PeriodicProcessDeploymentStatus,
  RetryingDeploy,
  Scheduled
}

import java.time.LocalDateTime

// TODO: We should separate schedules concept from deployments - fully switch to ScheduleData and ScheduleDeploymentData
case class PeriodicProcessDeployment(
    id: PeriodicProcessDeploymentId,
    periodicProcess: PeriodicProcess,
    createdAt: LocalDateTime,
    runAt: LocalDateTime,
    scheduleName: ScheduleName,
    retriesLeft: Int,
    nextRetryAt: Option[LocalDateTime],
    state: PeriodicProcessDeploymentState
) {

  def display: String =
    s"Process with id=${periodicProcess.deploymentData.processId}, name=${periodicProcess.deploymentData.processName}, versionId=${periodicProcess.deploymentData.versionId}, scheduleName=${scheduleName.display} and deploymentId=$id"

  def toDetails: ScheduledDeploymentDetails =
    ScheduledDeploymentDetails(
      id = id.value,
      processName = periodicProcess.deploymentData.processName,
      versionId = periodicProcess.deploymentData.versionId,
      scheduleName = scheduleName.value,
      createdAt = createdAt,
      runAt = runAt,
      deployedAt = state.deployedAt,
      completedAt = state.completedAt,
      status = state.status match {
        case Scheduled      => ScheduledDeploymentStatus.Scheduled
        case Deployed       => ScheduledDeploymentStatus.Deployed
        case Finished       => ScheduledDeploymentStatus.Finished
        case Failed         => ScheduledDeploymentStatus.Failed
        case RetryingDeploy => ScheduledDeploymentStatus.RetryingDeploy
        case FailedOnDeploy => ScheduledDeploymentStatus.FailedOnDeploy
      },
    )

}

case class PeriodicProcessDeploymentState(
    deployedAt: Option[LocalDateTime],
    completedAt: Option[LocalDateTime],
    status: PeriodicProcessDeploymentStatus
)

case class PeriodicProcessDeploymentId(value: Long) {
  override def toString: String = value.toString
}

object PeriodicProcessDeploymentStatus extends Enumeration {
  type PeriodicProcessDeploymentStatus = Value

  val Scheduled, Deployed, Finished, Failed, RetryingDeploy, FailedOnDeploy = Value
}

case class ScheduleName(value: Option[String]) {
  def display: String = value.getOrElse("[default]")
}
