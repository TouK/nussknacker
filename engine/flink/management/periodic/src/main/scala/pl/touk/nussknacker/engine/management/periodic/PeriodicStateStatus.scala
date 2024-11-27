package pl.touk.nussknacker.engine.management.periodic

import pl.touk.nussknacker.engine.api.deployment.ProcessStateDefinitionManager.ProcessStatus
import pl.touk.nussknacker.engine.api.deployment.StateStatus.StatusName
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus.ProblemStateStatus
import pl.touk.nussknacker.engine.api.deployment.{
  ScenarioActionName,
  ScenarioActionTooltip,
  StateDefinitionDetails,
  StateStatus
}

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

  val statusActionsPF: PartialFunction[ProcessStatus, List[ScenarioActionName]] = {
    case ProcessStatus(SimpleStateStatus.Running, _, _) =>
      List(ScenarioActionName.Cancel) // periodic processes cannot be redeployed from GUI
    case ProcessStatus(_: ScheduledStatus, latestVersionId, deployedVersionId)
        if deployedVersionId.contains(latestVersionId) =>
      List(ScenarioActionName.Cancel, ScenarioActionName.Deploy, ScenarioActionName.PerformSingleExecution)
    case ProcessStatus(_: ScheduledStatus, _, _) =>
      List(ScenarioActionName.Cancel, ScenarioActionName.Deploy)
    case ProcessStatus(WaitingForScheduleStatus, _, _) =>
      List(ScenarioActionName.Cancel) // or maybe should it be empty??
    case ProcessStatus(_: ProblemStateStatus, _, _) =>
      List(ScenarioActionName.Cancel) // redeploy is not allowed
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

  def customActionTooltips(processStatus: ProcessStatus): Map[ScenarioActionName, ScenarioActionTooltip] = {
    processStatus match {
      case ProcessStatus(_: ScheduledStatus, latestVersionId, deployedVersionId)
          if deployedVersionId.contains(latestVersionId) =>
        Map.empty
      case ProcessStatus(_: ScheduledStatus, _, _) =>
        Map(ScenarioActionName.PerformSingleExecution -> ScenarioActionTooltip.NotAllowedForDeployedVersion)
      case ProcessStatus(_, _, _) =>
        Map(ScenarioActionName.PerformSingleExecution -> ScenarioActionTooltip.NotAllowedInCurrentState)
    }
  }

}
