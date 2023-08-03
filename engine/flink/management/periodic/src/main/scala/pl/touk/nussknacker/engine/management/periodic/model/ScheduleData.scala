package pl.touk.nussknacker.engine.management.periodic.model

import pl.touk.nussknacker.engine.management.periodic.db.{PeriodicProcessDeploymentEntity, PeriodicProcessesRepository}

import java.time.LocalDateTime

case class ScheduleData(process: PeriodicProcess, latestDeployments: List[ScheduleDeploymentData])

// To identify schedule we need scheduleName - None for SingleScheduleProperty and Some(key) for MultipleScheduleProperty keys
// Also we need PeriodicProcessId to distinguish between active schedules and some inactive from the past for the same PeriodicProcessId
// Warning: PeriodicProcessId is not the same as ProcessId - we match processes with schedules by process_name
case class ScheduleId(id: PeriodicProcessId, scheduleName: Option[String])

case class ScheduleDeploymentData(id: PeriodicProcessDeploymentId,
                                  runAt: LocalDateTime,
                                  retriesLeft: Int,
                                  nextRetryAt: Option[LocalDateTime],
                                  state: PeriodicProcessDeploymentState) {
  def toProcessDeployment(process: PeriodicProcess, scheduleName: Option[String]): PeriodicProcessDeployment =
    PeriodicProcessDeployment(id, process, runAt, scheduleName, retriesLeft, nextRetryAt, state)
}

object ScheduleDeploymentData {

  def apply(deployment: PeriodicProcessDeploymentEntity): ScheduleDeploymentData = {
    ScheduleDeploymentData(
      deployment.id,
      deployment.runAt,
      deployment.retriesLeft,
      deployment.nextRetryAt,
      PeriodicProcessesRepository.createPeriodicDeploymentState(deployment))
  }

}