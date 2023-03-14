package pl.touk.nussknacker.engine.management.periodic

import pl.touk.nussknacker.engine.api.deployment.ProcessActionType.ProcessActionType
import pl.touk.nussknacker.engine.api.deployment.StateStatus.StatusName
import pl.touk.nussknacker.engine.api.deployment.{CustomStateStatus, FailedStateStatus, ProcessActionType, RunningStateStatus, StateDefinitionDetails, StateStatus}

import java.net.URI
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object PeriodicStateStatus {

  //without seconds because we do not deploy with that precision
  private val format = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

  implicit class RichLocalDateTime(ldt: LocalDateTime) {
    def pretty: String = ldt.format(format)
  }

  val statusActionsPF: PartialFunction[StateStatus, List[ProcessActionType]] = {
    case _: RunningStateStatus => List(ProcessActionType.Cancel) //periodic processes cannot be redeployed from GUI
    case _: ScheduledStatus => List(ProcessActionType.Cancel, ProcessActionType.Deploy)
    case WaitingForScheduleStatus => List(ProcessActionType.Cancel) //or maybe should it be empty??
    case _: FailedStateStatus => List(ProcessActionType.Cancel) //original FailedStateStatus allows redeployment
  }

  val statusTooltipsPF: PartialFunction[StateStatus, Option[String]] = {
    case ScheduledStatus(nextRunAt) => Some(s"Scheduled at ${nextRunAt.pretty}")
  }

  val statusDescriptionsPF: PartialFunction[StateStatus, Option[String]] = {
    case ScheduledStatus(nextRunAt) => Some(s"Scheduled at ${nextRunAt.pretty}")
  }

  val customStateDefinitions: Map[StatusName, StateDefinitionDetails] = Map(
    ScheduledStatus.name -> StateDefinitionDetails(
      displayableName = "Scheduled",
      icon = Some(URI.create("/assets/states/scheduled.svg")),
      tooltip = Some("Scheduled"),
      description = None
    ),
    WaitingForScheduleStatus.name -> StateDefinitionDetails(
      displayableName = "Waiting for reschedule",
      icon = Some(URI.create("/assets/states/wait-reschedule.svg")),
      tooltip = Some("Finished. Waiting for reschedule"),
      description = None
    ),
  )

  case class ScheduledStatus(nextRunAt: LocalDateTime) extends CustomStateStatus(ScheduledStatus.name) {
    override def isRunning: Boolean = true
  }

  case object ScheduledStatus {
    val name = "SCHEDULED"
  }

  case object WaitingForScheduleStatus extends CustomStateStatus("WAITING_FOR_SCHEDULE") {
    override def isRunning: Boolean = true
  }
}
