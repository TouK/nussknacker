package pl.touk.nussknacker.ui.api

import pl.touk.nussknacker.engine.api.deployment.ProcessStateDefinitionManager.ScenarioStatusWithScenarioContext
import pl.touk.nussknacker.engine.api.deployment.StatusDetails
import pl.touk.nussknacker.engine.api.process.VersionId
import pl.touk.nussknacker.restmodel.scenariodetails.{LegacyScenarioStatusNameDto, ScenarioStatusDto}
import pl.touk.nussknacker.ui.process.deployment.DeploymentManagerDispatcher
import pl.touk.nussknacker.ui.process.repository.ScenarioWithDetailsEntity
import pl.touk.nussknacker.ui.security.api.LoggedUser

class ScenarioStatusPresenter(dispatcher: DeploymentManagerDispatcher) {

  def toDto(
      statusDetails: StatusDetails,
      processDetails: ScenarioWithDetailsEntity[_],
      currentlyPresentedVersionId: Option[VersionId]
  )(implicit user: LoggedUser): ScenarioStatusDto = {
    val presentation = dispatcher
      .deploymentManagerUnsafe(processDetails.processingType)
      .processStateDefinitionManager
      .statusPresentation(
        ScenarioStatusWithScenarioContext(
          scenarioStatusDetails = statusDetails,
          latestVersionId = processDetails.processVersionId,
          deployedVersionId = processDetails.lastDeployedAction.map(_.processVersionId),
          currentlyPresentedVersionId = currentlyPresentedVersionId
        )
      )
    ScenarioStatusDto(
      statusName = statusDetails.status.name,
      status = LegacyScenarioStatusNameDto(statusDetails.status.name),
      visibleActions = presentation.visibleActions,
      allowedActions = presentation.allowedActions.toList.sortBy(_.value),
      actionTooltips = presentation.actionTooltips,
      icon = presentation.icon,
      tooltip = presentation.tooltip,
      description = presentation.description,
    )
  }

}
