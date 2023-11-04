package pl.touk.nussknacker.restmodel.scenariodetails

import io.circe.generic.JsonCodec
import pl.touk.nussknacker.engine.api.component.ProcessingMode
import pl.touk.nussknacker.engine.api.deployment.{ProcessAction, ProcessState}
import pl.touk.nussknacker.engine.api.displayedgraph.DisplayableProcess
import pl.touk.nussknacker.engine.api.process.{
  ProcessId => ApiProcessId,
  ProcessIdWithName,
  ProcessName,
  ProcessingType,
  ScenarioVersion,
  VersionId
}
import pl.touk.nussknacker.engine.processingtypesetup.EngineSetupName
import pl.touk.nussknacker.restmodel.validation.{ValidatedDisplayableProcess, ValidationResults}

import java.time.Instant

@JsonCodec
final case class ScenarioWithDetails(
    id: String,
    name: ProcessName,
    processId: ApiProcessId,
    processVersionId: VersionId,
    isLatestVersion: Boolean,
    description: Option[String],
    isArchived: Boolean,
    isFragment: Boolean,
    processingType: ProcessingType,
    processCategory: String,
    modificationDate: Instant, // TODO: Deprecated, please use modifiedAt
    modifiedAt: Instant,
    modifiedBy: String,
    createdAt: Instant,
    createdBy: String,
    tags: Option[List[String]],
    lastDeployedAction: Option[ProcessAction],
    lastStateAction: Option[ProcessAction],
    lastAction: Option[ProcessAction],
    // TODO: move things like processingType, category and validationResult on the root level and rename json to scenarioGraph
    json: Option[ValidatedDisplayableProcess],
    history: Option[List[ScenarioVersion]],
    modelVersion: Option[Int],
    state: Option[ProcessState],
    processingMode: ProcessingMode,
    engineSetupName: EngineSetupName
) {

  lazy val idWithName: ProcessIdWithName = ProcessIdWithName(processId, name)

  def withScenarioGraphAndValidationResult(
      scenarioWithValidationResult: ValidatedDisplayableProcess
  ): ScenarioWithDetails = {
    copy(json = Some(scenarioWithValidationResult))
  }

  def historyUnsafe: List[ScenarioVersion] = history.getOrElse(throw new IllegalStateException("Missing history"))

  def scenarioGraphUnsafe: DisplayableProcess = scenarioGraphAndValidationResultUnsafe.toDisplayable

  def validationResultUnsafe: ValidationResults.ValidationResult =
    validationResult.getOrElse(throw new IllegalStateException("Missing validation result"))

  def validationResult: Option[ValidationResults.ValidationResult] = {
    scenarioGraphAndValidationResultUnsafe.validationResult
  }

  def scenarioGraphAndValidationResultUnsafe: ValidatedDisplayableProcess =
    json.getOrElse(throw new IllegalStateException("Missing scenario graph and validation result"))

}
