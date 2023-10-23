package pl.touk.nussknacker.restmodel

import io.circe.generic.JsonCodec
import pl.touk.nussknacker.engine.api.deployment.{ProcessAction, ProcessState}
import pl.touk.nussknacker.engine.api.process.{ProcessId => ApiProcessId, ProcessIdWithName, ProcessName, VersionId}
import pl.touk.nussknacker.restmodel.displayedgraph.{DisplayableProcess, ValidatedDisplayableProcess}
import pl.touk.nussknacker.restmodel.process.ProcessingType
import pl.touk.nussknacker.restmodel.processdetails.{BaseProcessDetails, BasicProcess, ProcessVersion}
import pl.touk.nussknacker.restmodel.validation.ValidationResults

import java.time.Instant

// TODO: Rename, clean up
@JsonCodec
final case class ValidatedProcessDetails(
    id: String,
    name: String,
    processId: ApiProcessId,
    processVersionId: VersionId,
    isLatestVersion: Boolean,
    description: Option[String],
    isArchived: Boolean,
    isFragment: Boolean,
    processingType: ProcessingType,
    processCategory: String,
    modificationDate: Instant,
    modifiedAt: Instant,
    modifiedBy: String,
    createdAt: Instant,
    createdBy: String,
    tags: List[String],
    lastDeployedAction: Option[ProcessAction],
    lastStateAction: Option[ProcessAction],
    lastAction: Option[ProcessAction],
    // TODO: move things like processingType, category and validationResult on the root level and rename json to scenarioGraph or sth similar
    json: Option[ValidatedDisplayableProcess],
    history: List[ProcessVersion],
    modelVersion: Option[Int],
    state: Option[ProcessState]
) {

  lazy val idWithName: ProcessIdWithName = ProcessIdWithName(processId, ProcessName(name))

  def withScenarioGraphAndValidationResult(
      scenarioWithValidationResult: ValidatedDisplayableProcess
  ): ValidatedProcessDetails = {
    copy(json = Some(scenarioWithValidationResult))
  }

  // TODO: Instead of doing these conversions below, pass around ValidatedProcessDetails
  def toBasicProcess: BasicProcess = BasicProcess(toProcessDetailsWithoutScenarioGraphAndValidationResult)

  def toProcessDetailsWithoutScenarioGraphAndValidationResult: BaseProcessDetails[Unit] = {
    toBaseProcessDetails(())
  }

  def toProcessDetailsWithScenarioGraphUnsafe: BaseProcessDetails[DisplayableProcess] = {
    toBaseProcessDetails(scenarioGraphAndValidationResultUnsafe.toDisplayable)
  }

  // TODO: replace by toProcessDetailsWithoutScenarioGraphAndValidationResult?
  private def toBaseProcessDetails[T](prepareJson: => T): BaseProcessDetails[T] = {
    BaseProcessDetails(
      id = id,
      name = name,
      processId = processId,
      processVersionId = processVersionId,
      isLatestVersion = isLatestVersion,
      description = description,
      isArchived = isArchived,
      isFragment = isFragment,
      processingType = processingType,
      processCategory = processCategory,
      modificationDate = modificationDate,
      modifiedAt = modifiedAt,
      modifiedBy = modifiedBy,
      createdAt = createdAt,
      createdBy = createdBy,
      tags = tags,
      lastDeployedAction = lastDeployedAction,
      lastStateAction = lastStateAction,
      lastAction = lastAction,
      json = prepareJson,
      history = history,
      modelVersion = modelVersion,
      state = state
    )
  }

  def scenarioGraphUnsafe: DisplayableProcess = scenarioGraphAndValidationResultUnsafe.toDisplayable

  def validationResultUnsafe: ValidationResults.ValidationResult =
    validationResult.getOrElse(throw new IllegalStateException("Missing validation result"))

  def validationResult: Option[ValidationResults.ValidationResult] = {
    scenarioGraphAndValidationResultUnsafe.validationResult
  }

  private def scenarioGraphAndValidationResultUnsafe: ValidatedDisplayableProcess =
    json.getOrElse(throw new IllegalStateException("Missing scenario graph and validation result"))

}

object ValidatedProcessDetails {

  def apply(details: BaseProcessDetails[ValidatedDisplayableProcess]): ValidatedProcessDetails =
    fromProcessDetailsIgnoringScenarioGraphAndValidationResult(details).withScenarioGraphAndValidationResult(
      details.json
    )

  def fromProcessDetailsIgnoringScenarioGraphAndValidationResult(
      details: BaseProcessDetails[_]
  ): ValidatedProcessDetails = {
    ValidatedProcessDetails(
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
      state = details.state
    )
  }

}
