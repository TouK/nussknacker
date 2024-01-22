package pl.touk.nussknacker.restmodel.scenariodetails

import io.circe.generic.JsonCodec
import pl.touk.nussknacker.engine.api.displayedgraph.DisplayableProcess
import pl.touk.nussknacker.engine.api.process.{ProcessName, ProcessingType, ScenarioVersion}
import pl.touk.nussknacker.restmodel.validation.ValidationResults
import pl.touk.nussknacker.restmodel.validation.ValidationResults.ValidationResult

@JsonCodec final case class ScenarioWithDetailsForMigrations(
    name: ProcessName,
    isArchived: Boolean,
    isFragment: Boolean,
    processingType: ProcessingType,
    processCategory: String,
    scenarioGraph: Option[DisplayableProcess],
    validationResult: Option[ValidationResult],
    history: Option[List[ScenarioVersion]],
    modelVersion: Option[Int],
) {

  def historyUnsafe: List[ScenarioVersion] = history.getOrElse(throw new IllegalStateException("Missing history"))

  def scenarioGraphUnsafe: DisplayableProcess =
    scenarioGraph.getOrElse(throw new IllegalStateException("Missing scenario graph and validation result"))

  def validationResultUnsafe: ValidationResults.ValidationResult =
    validationResult.getOrElse(throw new IllegalStateException("Missing validation result"))

}
