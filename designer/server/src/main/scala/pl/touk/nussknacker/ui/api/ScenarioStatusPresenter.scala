package pl.touk.nussknacker.ui.api

import pl.touk.nussknacker.engine.api.deployment.{NoAttributesStateStatus, StateStatus}
import pl.touk.nussknacker.engine.api.deployment.ProcessStateDefinitionManager.ScenarioStatusWithScenarioContext
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus
import pl.touk.nussknacker.engine.api.process.VersionId
import pl.touk.nussknacker.restmodel.scenariodetails.{
  ScenarioStatusDetailsDto,
  ScenarioStatusDto,
  ScenarioStatusNameWrapperDto,
  ScenarioWithDetails
}
import pl.touk.nussknacker.ui.process.deployment.DeploymentManagerDispatcher
import pl.touk.nussknacker.ui.process.periodic.{PeriodicProcessService, PeriodicStateStatus}
import pl.touk.nussknacker.ui.security.api.LoggedUser

class ScenarioStatusPresenter(dispatcher: DeploymentManagerDispatcher) {

  def toDto(
      scenarioStatus: StateStatus,
      processDetails: ScenarioWithDetails,
      currentlyPresentedVersionId: Option[VersionId]
  )(implicit user: LoggedUser): ScenarioStatusDto = {
    val presentation = dispatcher
      .deploymentManagerUnsafe(processDetails.processingType)
      .processStateDefinitionManager
      .statusPresentation(
        ScenarioStatusWithScenarioContext(
          scenarioStatus = scenarioStatus,
          deployedVersionId = processDetails.lastDeployedAction.map(_.processVersionId),
          currentlyPresentedVersionId = currentlyPresentedVersionId
        )
      )
    ScenarioStatusDto(
      status = toDto(scenarioStatus),
      visibleActions = presentation.visibleActions.toList.sortBy(_.value),
      allowedActions = presentation.allowedActions.toList.sortBy(_.value),
      actionTooltips = presentation.actionTooltips,
      icon = presentation.icon,
      tooltip = presentation.tooltip,
      description = presentation.description,
    )
  }

  private def toDto(scenarioStatus: StateStatus): ScenarioStatusDetailsDto = scenarioStatus match {
    case SimpleStateStatus.Running(version, startedAt) =>
      ScenarioStatusDetailsDto.Running(version.value.toString, startedAt)
    case other => ScenarioStatusDetailsDto.NoAttributesStatus(other.name)
  }

}
