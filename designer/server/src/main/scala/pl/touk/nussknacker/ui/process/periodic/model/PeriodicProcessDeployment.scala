package pl.touk.nussknacker.ui.process.periodic.model

import PeriodicProcessDeploymentStatus.PeriodicProcessDeploymentStatus

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
