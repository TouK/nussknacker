package pl.touk.nussknacker.ui.process.periodic

import pl.touk.nussknacker.engine.api.deployment.ProcessStateDefinitionManager.ProcessStatus
import pl.touk.nussknacker.engine.api.deployment.StateStatus.StatusName
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus.ProblemStateStatus
import pl.touk.nussknacker.engine.api.deployment.{ScenarioActionName, StateDefinitionDetails, StateStatus}
import pl.touk.nussknacker.engine.api.process.VersionId

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
    case ProcessStatus(SimpleStateStatus.Running, _, _, _) =>
      // periodic processes cannot be redeployed from GUI
      List(ScenarioActionName.Cancel)
    case ProcessStatus(_: ScheduledStatus, _, deployedVersionId, Some(currentlyPresentedVersionId))
        if deployedVersionId.contains(currentlyPresentedVersionId) =>
      List(ScenarioActionName.Cancel, ScenarioActionName.Deploy, ScenarioActionName.RunOffSchedule)
    case ProcessStatus(_: ScheduledStatus, _, _, None) =>
      // At the moment of deployment or validation, we may not have the information about the currently displayed version
      // In that case we assume, that it was validated before the deployment was initiated.
      List(ScenarioActionName.Cancel, ScenarioActionName.Deploy, ScenarioActionName.RunOffSchedule)
    case ProcessStatus(_: ScheduledStatus, _, _, _) =>
      List(ScenarioActionName.Cancel, ScenarioActionName.Deploy)
    case ProcessStatus(WaitingForScheduleStatus, _, _, _) =>
      List(ScenarioActionName.Cancel) // or maybe should it be empty??
    case ProcessStatus(_: ProblemStateStatus, _, _, _) =>
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

  def customActionTooltips(processStatus: ProcessStatus): Map[ScenarioActionName, String] = {
    processStatus match {
      case ProcessStatus(_: ScheduledStatus, _, deployedVersionId, currentlyPresentedVersionId)
          if currentlyPresentedVersionId == deployedVersionId =>
        Map.empty
      case ProcessStatus(_: ScheduledStatus, _, deployedVersionIdOpt, currentlyPresentedVersionId) =>
        def print(versionIdOpt: Option[VersionId]) = versionIdOpt match {
          case Some(versionId) => s"${versionId.value}"
          case None            => "[unknown]"
        }
        Map(
          ScenarioActionName.RunOffSchedule -> s"Version ${print(deployedVersionIdOpt)} is deployed, but different version ${print(currentlyPresentedVersionId)} is displayed"
        )
      case ProcessStatus(other, _, _, _) =>
        Map(ScenarioActionName.RunOffSchedule -> s"Disabled for ${other.name} status.")
    }
  }

}
