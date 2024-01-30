package pl.touk.nussknacker.restmodel.validation

import io.circe.generic.JsonCodec
import pl.touk.nussknacker.engine.api.graph.ScenarioGraph
import pl.touk.nussknacker.restmodel.validation.ValidationResults.ValidationResult

@JsonCodec final case class ScenarioGraphWithValidationResult(
    scenarioGraph: ScenarioGraph,
    validationResult: ValidationResult
)
