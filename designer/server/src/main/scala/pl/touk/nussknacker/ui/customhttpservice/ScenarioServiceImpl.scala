package pl.touk.nussknacker.ui.customhttpservice

import pl.touk.nussknacker.engine.api.process.ProcessId
import pl.touk.nussknacker.restmodel.scenariodetails.{ScenarioStatusDto, ScenarioWithDetails}
import pl.touk.nussknacker.ui.customhttpservice.services.ScenarioService
import pl.touk.nussknacker.ui.process.ProcessService.GetScenarioWithDetailsOptions
import pl.touk.nussknacker.ui.process.repository.ScenarioVersionMetadata
import pl.touk.nussknacker.ui.process.{ProcessService, ScenarioQuery}
import pl.touk.nussknacker.ui.security.api.LoggedUser

import scala.concurrent.{ExecutionContext, Future}
import scala.language.implicitConversions

class ScenarioServiceImpl(processService: ProcessService)(implicit executionContext: ExecutionContext)
    extends ScenarioService {

  override def getLatestProcessesWithDetails(
      query: ScenarioService.ScenarioQuery
  )(implicit user: LoggedUser): Future[List[ScenarioService.ScenarioWithDetails]] =
    processService
      .getLatestProcessesWithDetails(
        toDomain(query),
        GetScenarioWithDetailsOptions.withoutAdditionalFields.withFetchState
      )
      .map(_.map(toApi))

  override def getLatestVersionForProcesses(
      query: ScenarioService.ScenarioQuery,
      excludedUserNames: Set[String]
  )(implicit user: LoggedUser): Future[Map[ProcessId, ScenarioService.ScenarioVersionMetadata]] =
    processService
      .getLatestVersionForProcesses(toDomain(query), excludedUserNames)
      .map(_.map { case (processId, metadata) => (processId, toApi(metadata)) })

  private def toDomain(query: ScenarioService.ScenarioQuery): ScenarioQuery =
    ScenarioQuery(
      isFragment = query.isFragment,
      isArchived = query.isArchived,
      isDeployed = query.isDeployed,
      categories = query.categories,
      processingTypes = query.processingTypes,
      names = query.names,
    )

  private def toApi(metadata: ScenarioVersionMetadata): ScenarioService.ScenarioVersionMetadata =
    ScenarioService.ScenarioVersionMetadata(
      versionId = metadata.versionId,
      createdAt = metadata.createdAt,
      createdByUser = metadata.createdByUser,
    )

  private def toApi(scenario: ScenarioWithDetails): ScenarioService.ScenarioWithDetails =
    ScenarioService.ScenarioWithDetails(
      name = scenario.name,
      processId = scenario.processId,
      processVersionId = scenario.processVersionId,
      isLatestVersion = scenario.isLatestVersion,
      description = scenario.description,
      isArchived = scenario.isArchived,
      isFragment = scenario.isFragment,
      processingType = scenario.processingType,
      processCategory = scenario.processCategory,
      processingMode = scenario.processingMode,
      engineSetupName = scenario.engineSetupName,
      modifiedAt = scenario.modifiedAt,
      modifiedBy = scenario.modifiedBy,
      createdAt = scenario.createdAt,
      createdBy = scenario.createdBy,
      labels = scenario.labels,
      lastDeployedAction = scenario.lastDeployedAction,
      lastStateAction = scenario.lastStateAction,
      lastAction = scenario.lastAction,
      modelVersion = scenario.modelVersion,
      state = scenario.state.map(toApi),
    )

  private def toApi(scenario: ScenarioStatusDto): ScenarioService.ScenarioStatus =
    ScenarioService.ScenarioStatus(
      status = scenario.status.name,
      visibleActions = scenario.visibleActions,
      allowedActions = scenario.allowedActions,
      actionTooltips = scenario.actionTooltips,
      icon = scenario.icon,
      tooltip = scenario.tooltip,
      description = scenario.description,
    )

}
