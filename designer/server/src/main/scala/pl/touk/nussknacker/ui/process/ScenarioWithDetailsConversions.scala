package pl.touk.nussknacker.ui.process

import pl.touk.nussknacker.restmodel.displayedgraph.{DisplayableProcess, ValidatedDisplayableProcess}
import pl.touk.nussknacker.restmodel.scenariodetails.ScenarioWithDetails
import pl.touk.nussknacker.ui.process.repository.ScenarioWithDetailsEntity

object ScenarioWithDetailsConversions {

  def fromRepositoryDetails(details: ScenarioWithDetailsEntity[ValidatedDisplayableProcess]): ScenarioWithDetails =
    fromRepositoryDetailsIgnoringGraphAndValidationResult(details).withScenarioGraphAndValidationResult(
      details.json
    )

  def fromRepositoryDetailsWithScenarioGraph(
      details: ScenarioWithDetailsEntity[DisplayableProcess]
  ): ScenarioWithDetails =
    fromRepositoryDetailsIgnoringGraphAndValidationResult(details).withScenarioGraphAndValidationResult(
      ValidatedDisplayableProcess.withEmptyValidationResult(details.json)
    )

  def fromRepositoryDetailsIgnoringGraphAndValidationResult(
      details: ScenarioWithDetailsEntity[_]
  ): ScenarioWithDetails = {
    ScenarioWithDetails(
      id = details.id,
      name = details.name,
      processId = details.processId,
      processVersionId = details.processVersionId,
      isLatestVersion = details.isLatestVersion,
      description = details.description,
      isArchived = details.isArchived,
      isFragment = details.isFragment,
      processingType = details.processingType,
      processCategory = details.processCategory,
      modificationDate = details.modificationDate,
      modifiedAt = details.modifiedAt,
      modifiedBy = details.modifiedBy,
      createdAt = details.createdAt,
      createdBy = details.createdBy,
      tags = details.tags,
      lastDeployedAction = details.lastDeployedAction,
      lastStateAction = details.lastStateAction,
      lastAction = details.lastAction,
      json = None,
      history = details.history,
      modelVersion = details.modelVersion,
      state = None
    )
  }

  implicit class Ops(scenarioWithDetails: ScenarioWithDetails) {

    // TODO: Instead of doing these conversions below, wee should pass around ScenarioWithDetails
    def toRepositoryDetailsWithoutScenarioGraphAndValidationResult: ScenarioWithDetailsEntity[Unit] = {
      toRepositoryDetails(())
    }

    def toRepositoryDetailsWithScenarioGraphUnsafe: ScenarioWithDetailsEntity[DisplayableProcess] = {
      toRepositoryDetails(scenarioWithDetails.scenarioGraphAndValidationResultUnsafe.toDisplayable)
    }

    private def toRepositoryDetails[T](prepareJson: => T): ScenarioWithDetailsEntity[T] = {
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
