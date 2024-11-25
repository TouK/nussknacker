package pl.touk.nussknacker.engine.common.periodic

import pl.touk.nussknacker.engine.api.deployment.StateStatus.StatusName
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus.ProblemStateStatus
import pl.touk.nussknacker.engine.api.deployment.{ScenarioActionName, StateDefinitionDetails, StateStatus}

import java.net.URI
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object PeriodicStateStatus {

  // without seconds because we do not deploy with that precision
  val Format: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

  implicit class RichLocalDateTime(ldt: LocalDateTime) {
    def pretty: String = ldt.format(Format)
  }

  case class ScheduledStatus(nextRunAt: LocalDateTime) extends StateStatus {
    override def name: StatusName = ScheduledStatus.name
  }

  case object ScheduledStatus {
    val name = "SCHEDULED"

    def isScheduledStatus(status: StateStatus): Boolean = {
      status.name == name
    }

  }

  val WaitingForScheduleStatus: StateStatus = StateStatus("WAITING_FOR_SCHEDULE")

  val statusActionsPF: PartialFunction[StateStatus, List[ScenarioActionName]] = {
    case SimpleStateStatus.Running =>
      List(ScenarioActionName.Cancel) // periodic processes cannot be redeployed from GUI
    case _: ScheduledStatus       => List(ScenarioActionName.Cancel, ScenarioActionName.Deploy)
    case WaitingForScheduleStatus => List(ScenarioActionName.Cancel) // or maybe should it be empty??
    case _: ProblemStateStatus    => List(ScenarioActionName.Cancel) // redeploy is not allowed
  }

  val statusTooltipsPF: PartialFunction[StateStatus, String] = { case ScheduledStatus(nextRunAt) =>
    s"Scheduled at ${nextRunAt.pretty}"
  }

  val statusDescriptionsPF: PartialFunction[StateStatus, String] = { case ScheduledStatus(nextRunAt) =>
    s"Scheduled at ${nextRunAt.pretty}"
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

}
