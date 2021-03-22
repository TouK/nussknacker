package pl.touk.nussknacker.engine.management.periodic

import java.net.URI
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api.deployment.ProcessActionType.ProcessActionType
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus
import pl.touk.nussknacker.engine.api.deployment.{FailedStateStatus, ProcessActionType, ProcessStateDefinitionManager, RunningStateStatus, StateStatus}

class PeriodicProcessStateDefinitionManager(delegate: ProcessStateDefinitionManager) extends ProcessStateDefinitionManager  with LazyLogging{

  override def statusActions(stateStatus: StateStatus): List[ProcessActionType] = stateStatus match {
    case _: RunningStateStatus => List(ProcessActionType.Cancel) //periodic processes cannot be redeployed from GUI
    case _: ScheduledStatus => List(ProcessActionType.Cancel, ProcessActionType.Deploy)
    case WaitingForScheduleStatus => List(ProcessActionType.Cancel) //or maybe should it be empty??
    case _: FailedStateStatus => List(ProcessActionType.Cancel) //original FailedStateStatus allows redeployment
    case _ => delegate.statusActions(stateStatus)
  }

  import PeriodicProcessStateDefinitionManager._

  override def statusTooltip(stateStatus: StateStatus): Option[String] = stateStatus match {
    case ScheduledStatus(nextRunAt) => Some(s"Scheduled at ${nextRunAt.pretty}")
    case WaitingForScheduleStatus => Some(s"Finished. Waiting for reschedule")
    case _ => delegate.statusTooltip(stateStatus)
  }

  override def statusDescription(stateStatus: StateStatus): Option[String] = stateStatus match {
    case ScheduledStatus(nextRunAt) => Some(s"Scheduled at ${nextRunAt.pretty}")
    case WaitingForScheduleStatus => Some(s"Finished. Waiting for reschedule")
    case _ => delegate.statusDescription(stateStatus)
  }

  override def statusIcon(stateStatus: StateStatus): Option[URI] = stateStatus match {
    case WaitingForScheduleStatus => Some(URI.create("/assets/states/wait-reschedule.svg"))
    case ScheduledStatus(_) => Some(URI.create("/assets/states/scheduled.svg"))
    case _ => delegate.statusIcon(stateStatus)
  }

  override def mapActionToStatus(stateAction: Option[ProcessActionType]): StateStatus = delegate.mapActionToStatus(stateAction)
}

object PeriodicProcessStateDefinitionManager {
  //bez sekund bo z taka dokładnością i tak nie deployujemy
  private val format = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
  implicit class RichLocalDateTime(ldt: LocalDateTime) {
    def pretty: String = ldt.format(format)
  }

}
