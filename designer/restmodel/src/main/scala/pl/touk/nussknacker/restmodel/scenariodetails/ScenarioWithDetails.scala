package pl.touk.nussknacker.restmodel.scenariodetails

import io.circe.{Decoder, Encoder}
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.component.ProcessingMode
import pl.touk.nussknacker.engine.api.deployment.{ProcessAction, ProcessState}
import pl.touk.nussknacker.engine.api.graph.ScenarioGraph
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.deployment.EngineSetupName
import pl.touk.nussknacker.restmodel.validation.ValidationResults
import pl.touk.nussknacker.restmodel.validation.ValidationResults.ValidationResult

import java.time.Instant

final case class ScenarioWithDetails(
    override val name: ProcessName,
    // TODO: This field is not passed to FE as we always use ProcessName in our API (see the encoder below)
    //       We should extract another DTO class without this one field, and move this class with defined processId
    //       into our domain model
    processId: Option[ProcessId],
    processVersionId: VersionId,
    isLatestVersion: Boolean,
    description: Option[String],
    override val isArchived: Boolean,
    override val isFragment: Boolean,
    override val processingType: ProcessingType,
    override val processCategory: String,
    processingMode: ProcessingMode,
    engineSetupName: EngineSetupName,
    modificationDate: Instant, // TODO: Deprecated, please use modifiedAt
    modifiedAt: Instant,
    modifiedBy: String,
    createdAt: Instant,
    createdBy: String,
    override val labels: List[String],
    // actions are used only by external project
    lastDeployedAction: Option[ProcessAction],
    lastStateAction: Option[ProcessAction],
    lastAction: Option[ProcessAction],
    override val scenarioGraph: Option[ScenarioGraph],
    override val validationResult: Option[ValidationResult],
    override val history: Option[List[ScenarioVersion]],
    override val modelVersion: Option[Int],
    state: Option[ProcessState],
    additionalDetails: Option[Map[String, String]],
) extends BaseScenarioWithDetailsForMigrations {

  def parameters: ScenarioParameters = ScenarioParameters(processingMode, processCategory, engineSetupName)

  def withScenarioGraph(scenarioGraph: ScenarioGraph): ScenarioWithDetails =
    copy(scenarioGraph = Some(scenarioGraph))

  def withValidationResult(validationResult: ValidationResult): ScenarioWithDetails =
    copy(validationResult = Some(validationResult))

  def scenarioGraphUnsafe: ScenarioGraph =
    scenarioGraph.getOrElse(throw new IllegalStateException("Missing scenario graph and validation result"))

  def validationResultUnsafe: ValidationResults.ValidationResult =
    validationResult.getOrElse(throw new IllegalStateException("Missing validation result"))

  def idWithNameUnsafe: ProcessIdWithName =
    ProcessIdWithName(processIdUnsafe, name)

  def processIdUnsafe: ProcessId = processId.getOrElse(throw new IllegalStateException("Missing processId"))

  def processVersionUnsafe: ProcessVersion = ProcessVersion(
    versionId = processVersionId,
    processName = name,
    processId = processIdUnsafe,
    labels = labels,
    user = createdBy,
    modelVersion = modelVersion
  )

}

object ScenarioWithDetails {

  import io.circe.generic.semiauto._

  // TODO: We remove null values from json to make response payload lighter.
  // The target solution would be to introduce pagination to the /api/processes endpoint.
  implicit val encoder: Encoder[ScenarioWithDetails] =
    deepDropNulls(
      deriveEncoder[ScenarioWithDetails]
        .contramap[ScenarioWithDetails](_.copy(processId = None))
    )

  implicit val decoder: Decoder[ScenarioWithDetails] =
    deriveDecoder[ScenarioWithDetails]

  private def deepDropNulls[A](encoder: Encoder[A]): Encoder[A] = encoder.mapJson(_.deepDropNullValues)

}

// This class is to enforce consistency of fields between CreateScenarioCommand and ScenarioWithDetails.
// Eventually we want to make our API more restfull. It means that posting DTOs should be similar to getting DTOs
// For example we should allow to pass scenarioGraph in POST. Currently we do it in two steps: create andThan update.
// Some of fields can't be added here because during POSTing they are optional and during GETing they are not
// (category, processingMode and engineSetupName)
trait BaseCreateScenarioCommand {
  def name: ProcessName
  def isFragment: Boolean
}
