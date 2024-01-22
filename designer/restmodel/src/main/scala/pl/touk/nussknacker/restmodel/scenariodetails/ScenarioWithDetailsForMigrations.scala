package pl.touk.nussknacker.restmodel.scenariodetails

import io.circe.generic.JsonCodec
import pl.touk.nussknacker.engine.api.displayedgraph.DisplayableProcess
import pl.touk.nussknacker.engine.api.process.{ProcessName, ProcessingType, ScenarioVersion}
import pl.touk.nussknacker.restmodel.validation.ValidationResults
import pl.touk.nussknacker.restmodel.validation.ValidationResults.ValidationResult

// It is a minimal set of information used by migration mechanism
@JsonCodec final case class ScenarioWithDetailsForMigrations(
    override val name: ProcessName,
    override val isArchived: Boolean,
    override val isFragment: Boolean,
    override val processingType: ProcessingType,
    override val processCategory: String,
    override val scenarioGraph: Option[DisplayableProcess],
    override val validationResult: Option[ValidationResult],
    override val history: Option[List[ScenarioVersion]],
    override val modelVersion: Option[Int],
) extends BaseScenarioWithDetailsForMigrations {

  def historyUnsafe: List[ScenarioVersion] = history.getOrElse(throw new IllegalStateException("Missing history"))

  def scenarioGraphUnsafe: DisplayableProcess =
    scenarioGraph.getOrElse(throw new IllegalStateException("Missing scenario graph and validation result"))

  def validationResultUnsafe: ValidationResults.ValidationResult =
    validationResult.getOrElse(throw new IllegalStateException("Missing validation result"))

}

// This trait is extracted for easier monitoring changes in the /processes api that have an influence on the migration API
trait BaseScenarioWithDetailsForMigrations {
  def name: ProcessName
  def isArchived: Boolean
  def isFragment: Boolean
  def processingType: ProcessingType
  def processCategory: String
  def scenarioGraph: Option[DisplayableProcess]
  def validationResult: Option[ValidationResult]
  def history: Option[List[ScenarioVersion]]
  def modelVersion: Option[Int]
}
