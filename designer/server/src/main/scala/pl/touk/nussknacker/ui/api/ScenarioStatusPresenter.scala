package pl.touk.nussknacker.ui.api

import pl.touk.nussknacker.engine.api.deployment.ProcessStateDefinitionManager.ScenarioStatusWithScenarioContext
import pl.touk.nussknacker.engine.api.deployment.StateStatus
import pl.touk.nussknacker.engine.api.process.VersionId
import pl.touk.nussknacker.restmodel.scenariodetails.{
  ScenarioStatusDto,
  ScenarioStatusNameWrapperDto,
  ScenarioWithDetails
}
import pl.touk.nussknacker.ui.process.deployment.DeploymentManagerDispatcher
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
          latestVersionId = processDetails.processVersionId,
          deployedVersionId = processDetails.lastDeployedAction.map(_.processVersionId),
          currentlyPresentedVersionId = currentlyPresentedVersionId
        )
      )
    ScenarioStatusDto(
      status = ScenarioStatusNameWrapperDto(scenarioStatus.name),
      visibleActions = presentation.visibleActions,
      allowedActions = presentation.allowedActions.toList.sortBy(_.value),
      actionTooltips = presentation.actionTooltips,
      icon = presentation.icon,
      tooltip = presentation.tooltip,
      description = presentation.description,
    )
  }

}
