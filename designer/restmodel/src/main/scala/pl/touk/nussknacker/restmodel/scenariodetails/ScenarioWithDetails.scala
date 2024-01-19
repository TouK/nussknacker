package pl.touk.nussknacker.restmodel.scenariodetails

import io.circe.{Decoder, Encoder}
import pl.touk.nussknacker.engine.api.deployment.{ProcessAction, ProcessState}
import pl.touk.nussknacker.engine.api.displayedgraph.DisplayableProcess
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.restmodel.validation.ValidationResults
import pl.touk.nussknacker.restmodel.validation.ValidationResults.ValidationResult

import java.time.Instant

final case class ScenarioWithDetails(
    name: ProcessName,
    // TODO: This field is not passed to FE as we always use ProcessName in our API (see the encoder below)
    //       We should extract another DTO class without this one field, and move this class with defined processId
    //       into our domain model
    processId: Option[ProcessId],
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
    scenarioGraph: Option[DisplayableProcess],
    validationResult: Option[ValidationResult],
    history: Option[List[ScenarioVersion]],
    modelVersion: Option[Int],
    state: Option[ProcessState]
) {

  def withScenarioGraph(scenarioGraph: DisplayableProcess): ScenarioWithDetails =
    copy(scenarioGraph = Some(scenarioGraph))

  def withValidationResult(validationResult: ValidationResult): ScenarioWithDetails =
    copy(validationResult = Some(validationResult))

  def historyUnsafe: List[ScenarioVersion] = history.getOrElse(throw new IllegalStateException("Missing history"))

  def scenarioGraphUnsafe: DisplayableProcess =
    scenarioGraph.getOrElse(throw new IllegalStateException("Missing scenario graph and validation result"))

  def validationResultUnsafe: ValidationResults.ValidationResult =
    validationResult.getOrElse(throw new IllegalStateException("Missing validation result"))

  def idWithNameUnsafe: ProcessIdWithName =
    ProcessIdWithName(processIdUnsafe, name)

  def processIdUnsafe: ProcessId = processId.getOrElse(throw new IllegalStateException("Missing processId"))

}

object ScenarioWithDetails {

  import io.circe.generic.semiauto._

  implicit val encoder: Encoder[ScenarioWithDetails] =
    deriveEncoder[ScenarioWithDetails]
      .contramap[ScenarioWithDetails](_.copy(processId = None))

  implicit val decoder: Decoder[ScenarioWithDetails] =
    deriveDecoder[ScenarioWithDetails]

}
