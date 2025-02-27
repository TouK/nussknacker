package pl.touk.nussknacker.ui.customhttpservice

import cats.effect.{Async, Sync}
import pl.touk.nussknacker.engine.api.process.ProcessId
import pl.touk.nussknacker.restmodel.scenariodetails.{ScenarioStatusDto, ScenarioWithDetails}
import pl.touk.nussknacker.ui.customhttpservice.services.ScenarioService
import pl.touk.nussknacker.ui.process.{ProcessService, ScenarioQuery, ScenarioVersionQuery}
import pl.touk.nussknacker.ui.process.ProcessService.GetScenarioWithDetailsOptions
import pl.touk.nussknacker.ui.process.repository.ScenarioVersionMetadata
import pl.touk.nussknacker.ui.security.api.LoggedUser

import scala.concurrent.{ExecutionContext, Future}
import scala.language.implicitConversions

class ProcessServiceBasedScenarioServiceAdapter[M[_]: Async](
    processService: ProcessService
)(implicit executionContext: ExecutionContext)
    extends ScenarioService[M] {

  override def getLatestProcessesWithDetails(
      query: ScenarioService.ScenarioQuery
  )(implicit user: LoggedUser): M[List[ScenarioService.ScenarioWithDetails]] =
    processService
      .getLatestProcessesWithDetails(
        toDomain(query),
        GetScenarioWithDetailsOptions.withoutAdditionalFields.withFetchState
      )
      .map(_.map(toApi))

  override def getLatestVersionForProcesses(
      query: ScenarioService.ScenarioQuery,
      scenarioVersionQuery: ScenarioService.ScenarioVersionQuery,
  )(implicit user: LoggedUser): M[Map[ProcessId, ScenarioService.ScenarioVersionMetadata]] =
    processService
      .getLatestVersionForProcesses(toDomain(query), toDomain(scenarioVersionQuery))
      .map(_.map { case (processId, metadata) => (processId, toApi(metadata)) })

  private implicit def deferToM[T](f: => Future[T]): M[T] = Async[M].fromFuture(Sync[M].delay(f))

  private def toDomain(query: ScenarioService.ScenarioQuery): ScenarioQuery =
    ScenarioQuery(
      isFragment = query.isFragment,
      isArchived = query.isArchived,
      isDeployed = query.isDeployed,
      categories = query.categories,
      processingTypes = query.processingTypes,
      names = query.names,
    )

  private def toDomain(query: ScenarioService.ScenarioVersionQuery): ScenarioVersionQuery =
    ScenarioVersionQuery(
      excludedUserNames = query.excludedUserNames
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
