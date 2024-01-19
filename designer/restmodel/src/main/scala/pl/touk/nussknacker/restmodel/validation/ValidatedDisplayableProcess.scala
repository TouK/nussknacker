package pl.touk.nussknacker.restmodel.validation

import io.circe.generic.JsonCodec
import pl.touk.nussknacker.engine.api.displayedgraph.DisplayableProcess
import pl.touk.nussknacker.restmodel.validation.ValidationResults.ValidationResult

@JsonCodec final case class ValidatedDisplayableProcess(
    scenarioGraph: DisplayableProcess,
    validationResult: ValidationResult
)
