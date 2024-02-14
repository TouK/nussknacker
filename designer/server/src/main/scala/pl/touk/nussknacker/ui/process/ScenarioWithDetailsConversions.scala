package pl.touk.nussknacker.ui.process

import pl.touk.nussknacker.engine.api.graph.ScenarioGraph
import pl.touk.nussknacker.engine.api.process.ProcessId
import pl.touk.nussknacker.restmodel.scenariodetails.{ScenarioParameters, ScenarioWithDetails}
import pl.touk.nussknacker.restmodel.validation.ScenarioGraphWithValidationResult
import pl.touk.nussknacker.ui.process.repository.ScenarioWithDetailsEntity

object ScenarioWithDetailsConversions {

  def fromEntity(
      details: ScenarioWithDetailsEntity[ScenarioGraphWithValidationResult],
      parameters: ScenarioParameters
  ): ScenarioWithDetails =
    fromEntityIgnoringGraphAndValidationResult(details, parameters)
      .withScenarioGraph(details.json.scenarioGraph)
      .withValidationResult(details.json.validationResult)

  def fromEntityWithScenarioGraph(
      details: ScenarioWithDetailsEntity[ScenarioGraph],
      parameters: ScenarioParameters
  ): ScenarioWithDetails =
    fromEntityIgnoringGraphAndValidationResult(details, parameters).withScenarioGraph(details.json)

  def fromEntityIgnoringGraphAndValidationResult(
      details: ScenarioWithDetailsEntity[_],
      parameters: ScenarioParameters
  ): ScenarioWithDetails = {
    ScenarioWithDetails(
      name = details.name,
      processId = Some(details.processId),
      processVersionId = details.processVersionId,
      isLatestVersion = details.isLatestVersion,
      description = details.description,
      isArchived = details.isArchived,
      isFragment = details.isFragment,
      processingType = details.processingType,
      processCategory = details.processCategory,
      processingMode = parameters.processingMode,
      engineSetupName = parameters.engineSetupName,
      modificationDate = details.modificationDate,
      modifiedAt = details.modifiedAt,
      modifiedBy = details.modifiedBy,
      createdAt = details.createdAt,
      createdBy = details.createdBy,
      tags = details.tags,
      lastDeployedAction = details.lastDeployedAction,
      lastStateAction = details.lastStateAction,
      lastAction = details.lastAction,
      scenarioGraph = None,
      validationResult = None,
      history = details.history,
      modelVersion = details.modelVersion,
      state = None
    )
  }

  implicit class Ops(scenarioWithDetails: ScenarioWithDetails) {

    // TODO: Instead of doing these conversions below, wee should pass around ScenarioWithDetails
    def toEntity: ScenarioWithDetailsEntity[Unit] = {
      toEntity(())
    }

    private def toEntity[T](prepareJson: => T): ScenarioWithDetailsEntity[T] = {
      ScenarioWithDetailsEntity(
        name = scenarioWithDetails.name,
        // We can't just use processIdUnsafe because it is used also for testMigrations which gets ScenarioWithDetails
        // via REST API so this information will be missing
        processId = scenarioWithDetails.processId.getOrElse(ProcessId(-1)),
        processVersionId = scenarioWithDetails.processVersionId,
        isLatestVersion = scenarioWithDetails.isLatestVersion,
        description = scenarioWithDetails.description,
        isArchived = scenarioWithDetails.isArchived,
        isFragment = scenarioWithDetails.isFragment,
        processingType = scenarioWithDetails.processingType,
        processCategory = scenarioWithDetails.processCategory,
        modificationDate = scenarioWithDetails.modificationDate,
        modifiedAt = scenarioWithDetails.modifiedAt,
        modifiedBy = scenarioWithDetails.modifiedBy,
        createdAt = scenarioWithDetails.createdAt,
        createdBy = scenarioWithDetails.createdBy,
        tags = scenarioWithDetails.tags,
        lastDeployedAction = scenarioWithDetails.lastDeployedAction,
        lastStateAction = scenarioWithDetails.lastStateAction,
        lastAction = scenarioWithDetails.lastAction,
        json = prepareJson,
        history = scenarioWithDetails.history,
        modelVersion = scenarioWithDetails.modelVersion
      )
    }

  }

}
