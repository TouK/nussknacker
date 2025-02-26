package pl.touk.nussknacker.restmodel.scenariodetails

import io.circe.{Codec, Decoder, Encoder}
import pl.touk.nussknacker.engine.api.graph.ScenarioGraph
import pl.touk.nussknacker.engine.api.process.{ProcessingType, ProcessName, ScenarioVersion}
import pl.touk.nussknacker.restmodel.validation.ValidationResults
import pl.touk.nussknacker.restmodel.validation.ValidationResults.ValidationResult

// It is a minimal set of information used by migration mechanism
final case class ScenarioWithDetailsForMigrations(
    override val name: ProcessName,
    override val isArchived: Boolean,
    override val isFragment: Boolean,
    override val processingType: ProcessingType,
    override val processCategory: String,
    override val labels: List[String],
    override val scenarioGraph: Option[ScenarioGraph],
    override val validationResult: Option[ValidationResult],
    override val history: Option[List[ScenarioVersion]],
    override val modelVersion: Option[Int],
) extends BaseScenarioWithDetailsForMigrations {

  def historyUnsafe: List[ScenarioVersion] = history.getOrElse(throw new IllegalStateException("Missing history"))

  def scenarioGraphUnsafe: ScenarioGraph =
    scenarioGraph.getOrElse(throw new IllegalStateException("Missing scenario graph and validation result"))

  def validationResultUnsafe: ValidationResults.ValidationResult =
    validationResult.getOrElse(throw new IllegalStateException("Missing validation result"))
}

object ScenarioWithDetailsForMigrations {

  implicit val codec: Codec[ScenarioWithDetailsForMigrations] = {
    implicit val labelsCodec: Codec[List[String]] = safeLabelsCodec
    io.circe.generic.semiauto.deriveCodec
  }

  // labels field was introduced to BaseScenarioWithDetailsForMigrations in the 1.18 version and old versions do not provide it
  // This fallback is needed to migrate the scenario to older versions of NU and can be removed in future releases
  private lazy val safeLabelsCodec: Codec[List[String]] = {
    Codec.from(
      Decoder[Option[List[String]]].map(_.getOrElse(List.empty[String])),
      Encoder[List[String]]
    )
  }

}

// This trait is extracted for easier monitoring changes in the /processes api that have an influence on the migration API
trait BaseScenarioWithDetailsForMigrations {
  def name: ProcessName
  def isArchived: Boolean
  def isFragment: Boolean
  def processingType: ProcessingType
  def processCategory: String
  def labels: List[String]
  def scenarioGraph: Option[ScenarioGraph]
  def validationResult: Option[ValidationResult]
  def history: Option[List[ScenarioVersion]]
  def modelVersion: Option[Int]
}
