package pl.touk.nussknacker.ui.process.periodic

import pl.touk.nussknacker.engine.api.deployment.ProcessStateDefinitionManager.ScenarioStatusWithScenarioContext
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
  }

  val WaitingForScheduleStatus: StateStatus = StateStatus("WAITING_FOR_SCHEDULE")

  val statusActionsPF: PartialFunction[ScenarioStatusWithScenarioContext, Set[ScenarioActionName]] =
    Function.unlift((input: ScenarioStatusWithScenarioContext) =>
      (input.status, input.deployedVersionId, input.currentlyPresentedVersionId) match {
        case (SimpleStateStatus.Running, _, _) =>
          // periodic processes cannot be redeployed from GUI
          Some(Set(ScenarioActionName.Cancel))
        case (_: ScheduledStatus, deployedVersionId, Some(currentlyPresentedVersionId))
            if deployedVersionId.contains(currentlyPresentedVersionId) =>
          Some(Set(ScenarioActionName.Cancel, ScenarioActionName.Deploy, ScenarioActionName.RunOffSchedule))
        case (_: ScheduledStatus, _, None) =>
          // At the moment of deployment or validation, we may not have the information about the currently displayed version
          // In that case we assume, that it was validated before the deployment was initiated.
          Some(Set(ScenarioActionName.Cancel, ScenarioActionName.Deploy, ScenarioActionName.RunOffSchedule))
        case (_: ScheduledStatus, _, _) =>
          Some(Set(ScenarioActionName.Cancel, ScenarioActionName.Deploy))
        case (WaitingForScheduleStatus, _, _) =>
          Some(Set(ScenarioActionName.Cancel)) // or maybe should it be empty??
        case (_: ProblemStateStatus, _, _) =>
          Some(Set(ScenarioActionName.Cancel)) // redeploy is not allowed
        case _ =>
          None
      }
    )

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

  def customActionTooltips(input: ScenarioStatusWithScenarioContext): Map[ScenarioActionName, String] = {
    input.status match {
      case _: ScheduledStatus if input.currentlyPresentedVersionId == input.deployedVersionId =>
        Map.empty
      case _: ScheduledStatus =>
        def print(versionIdOpt: Option[VersionId]) = versionIdOpt match {
          case Some(versionId) => s"${versionId.value}"
          case None            => "[unknown]"
        }
        Map(
          ScenarioActionName.RunOffSchedule -> s"Version ${print(input.deployedVersionId)} is deployed, but different version ${print(input.currentlyPresentedVersionId)} is displayed"
        )
      case other =>
        Map(ScenarioActionName.RunOffSchedule -> s"Disabled for ${other.name} status.")
    }
  }

}
