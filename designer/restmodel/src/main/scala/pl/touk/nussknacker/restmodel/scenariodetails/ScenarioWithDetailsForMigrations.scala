package pl.touk.nussknacker.restmodel.scenariodetails

import io.circe.generic.JsonCodec
import pl.touk.nussknacker.engine.api.component.ProcessingMode
import pl.touk.nussknacker.engine.api.graph.ScenarioGraph
import pl.touk.nussknacker.engine.api.process.{ProcessId, ProcessName, ProcessingType, ScenarioVersion}
import pl.touk.nussknacker.engine.deployment.EngineSetupName
import pl.touk.nussknacker.restmodel.validation.ValidationResults
import pl.touk.nussknacker.restmodel.validation.ValidationResults.ValidationResult
import sttp.tapir.Schema

// It is a minimal set of information used by migration mechanism
@JsonCodec final case class ScenarioWithDetailsForMigrations(
    override val name: ProcessName,
    override val isArchived: Boolean,
    override val isFragment: Boolean,
    override val processingType: ProcessingType,
    override val processCategory: String,
    override val scenarioGraph: Option[ScenarioGraph],
    override val validationResult: Option[ValidationResult],
    override val history: Option[List[ScenarioVersion]],
    override val modelVersion: Option[Int],
    override val processingMode: ProcessingMode,
    override val engineSetupName: EngineSetupName,
) extends BaseScenarioWithDetailsForMigrations {

  def parameters: ScenarioParameters = ScenarioParameters(processingMode, processCategory, engineSetupName)

  def historyUnsafe: List[ScenarioVersion] = history.getOrElse(throw new IllegalStateException("Missing history"))

  def scenarioGraphUnsafe: ScenarioGraph =
    scenarioGraph.getOrElse(throw new IllegalStateException("Missing scenario graph and validation result"))

  def validationResultUnsafe: ValidationResults.ValidationResult =
    validationResult.getOrElse(throw new IllegalStateException("Missing validation result"))
}

object ScenarioWithDetailsForMigrations {

  implicit val scenarioGraph: Schema[ScenarioWithDetailsForMigrations] = ???
}

// This trait is extracted for easier monitoring changes in the /processes api that have an influence on the migration API
trait BaseScenarioWithDetailsForMigrations {
  def name: ProcessName
  def isArchived: Boolean
  def isFragment: Boolean
  def processingType: ProcessingType
  def processingMode: ProcessingMode
  def engineSetupName: EngineSetupName
  def processCategory: String
  def scenarioGraph: Option[ScenarioGraph]
  def validationResult: Option[ValidationResult]
  def history: Option[List[ScenarioVersion]]
  def modelVersion: Option[Int]
}
