package pl.touk.nussknacker.engine.management.periodic

import pl.touk.nussknacker.engine.api.deployment.ProcessActionType.ProcessActionType
import pl.touk.nussknacker.engine.api.deployment.StateStatus.StatusName
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus.ProblemStateStatus
import pl.touk.nussknacker.engine.api.deployment.{ProcessActionType, StateDefinitionDetails, StateStatus}

import java.net.URI
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object PeriodicStateStatus {

  //without seconds because we do not deploy with that precision
  val Format: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

  implicit class RichLocalDateTime(ldt: LocalDateTime) {
    def pretty: String = ldt.format(Format)
  }

  val statusActionsPF: PartialFunction[StateStatus, List[ProcessActionType]] = {
    case s if s.name.equals(SimpleStateStatus.Running.name) => List(ProcessActionType.Cancel) //periodic processes cannot be redeployed from GUI
    case s if s.name.equals(ScheduledStatus.name) => List(ProcessActionType.Cancel, ProcessActionType.Deploy)
    case s if s.name.equals(WaitingForScheduleStatus.name) => List(ProcessActionType.Cancel) //or maybe should it be empty??
    case s if s.name.equals(ProblemStateStatus.name) => List(ProcessActionType.Cancel) //redeploy is not allowed
  }

  val statusTooltipsPF: PartialFunction[StateStatus, String] = {
    case ScheduledStatus(nextRunAt) => s"Scheduled at ${nextRunAt.pretty}"
  }

  val statusDescriptionsPF: PartialFunction[StateStatus, String] = {
    case ScheduledStatus(nextRunAt) => s"Scheduled at ${nextRunAt.pretty}"
  }

  val customStateDefinitions: Map[StatusName, StateDefinitionDetails] = Map(
    ScheduledStatus.name -> StateDefinitionDetails(
      displayableName = "Scheduled",
      icon = URI.create("/assets/states/scheduled.svg"),
      tooltip = "Scheduled",
      description = "Scheduled"
    ),
    WaitingForScheduleStatus.name -> StateDefinitionDetails(
      displayableName = "Waiting for reschedule",
      icon = URI.create("/assets/states/wait-reschedule.svg"),
      tooltip = "Finished. Waiting for reschedule",
      description = "Finished. Waiting for reschedule"
    ),
  )

  case class ScheduledStatus(nextRunAt: LocalDateTime) extends StateStatus(ScheduledStatus.name) {
    override def isRunning: Boolean = true
  }

  case object ScheduledStatus {
    val name = "SCHEDULED"
  }

  case object WaitingForScheduleStatus extends StateStatus("WAITING_FOR_SCHEDULE") {
    override def isRunning: Boolean = true
  }
}
