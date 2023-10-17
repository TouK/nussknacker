package pl.touk.nussknacker.ui.process

import pl.touk.nussknacker.engine.ProcessingTypeSetup
import pl.touk.nussknacker.restmodel.displayedgraph.{DisplayableProcess, ValidatedDisplayableProcess}
import pl.touk.nussknacker.restmodel.scenariodetails.ScenarioWithDetails
import pl.touk.nussknacker.ui.process.repository.ScenarioWithDetailsEntity

object ScenarioWithDetailsConversions {

  def fromEntity(
      entity: ScenarioWithDetailsEntity[ValidatedDisplayableProcess],
      processingTypeSetup: ProcessingTypeSetup
  ): ScenarioWithDetails =
    fromEntityIgnoringGraphAndValidationResult(entity, processingTypeSetup).withScenarioGraphAndValidationResult(
      entity.json
    )

  def fromEntityWithScenarioGraph(
      entity: ScenarioWithDetailsEntity[DisplayableProcess],
      processingTypeSetup: ProcessingTypeSetup
  ): ScenarioWithDetails =
    fromEntityIgnoringGraphAndValidationResult(entity, processingTypeSetup).withScenarioGraphAndValidationResult(
      ValidatedDisplayableProcess.withEmptyValidationResult(entity.json)
    )

  def fromEntityIgnoringGraphAndValidationResult(
      entity: ScenarioWithDetailsEntity[_],
      processingTypeSetup: ProcessingTypeSetup
  ): ScenarioWithDetails = {
    ScenarioWithDetails(
      id = entity.id,
      name = entity.name,
      processId = entity.processId,
      processVersionId = entity.processVersionId,
      isLatestVersion = entity.isLatestVersion,
      description = entity.description,
      isArchived = entity.isArchived,
      isFragment = entity.isFragment,
      processingType = entity.processingType,
      processCategory = entity.processCategory,
      modificationDate = entity.modificationDate,
      modifiedAt = entity.modifiedAt,
      modifiedBy = entity.modifiedBy,
      createdAt = entity.createdAt,
      createdBy = entity.createdBy,
      tags = entity.tags,
      lastDeployedAction = entity.lastDeployedAction,
      lastStateAction = entity.lastStateAction,
      lastAction = entity.lastAction,
      json = None,
      history = entity.history,
      modelVersion = entity.modelVersion,
      state = None,
      processingMode = processingTypeSetup.processingMode,
      engineSetupName = processingTypeSetup.engineSetupName
    )
  }

  implicit class Ops(scenarioWithDetails: ScenarioWithDetails) {

    // TODO: Instead of doing these conversions below, wee should pass around ScenarioWithDetails
    def toEntityWithoutScenarioGraphAndValidationResult: ScenarioWithDetailsEntity[Unit] = {
      toEntity(())
    }

    def toEntityWithScenarioGraphUnsafe: ScenarioWithDetailsEntity[DisplayableProcess] = {
      toEntity(scenarioWithDetails.scenarioGraphAndValidationResultUnsafe.toDisplayable)
    }

    private def toEntity[T](prepareJson: => T): ScenarioWithDetailsEntity[T] = {
      repository.ScenarioWithDetailsEntity(
        id = scenarioWithDetails.id,
        name = scenarioWithDetails.name,
        processId = scenarioWithDetails.processId,
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
