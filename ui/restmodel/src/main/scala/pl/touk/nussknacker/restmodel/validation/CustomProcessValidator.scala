package pl.touk.nussknacker.restmodel.validation

import com.typesafe.config.Config
import pl.touk.nussknacker.restmodel.displayedgraph.DisplayableProcess
import pl.touk.nussknacker.restmodel.validation.ValidationResults.ValidationResult

//TODO: Move to some designer extensions module or to common extensions module when we switch from CanonicalProcess to DisplayableProcess in all places
trait CustomProcessValidatorFactory {
  def validator(config: Config): CustomProcessValidator
}

trait CustomProcessValidator {
  def validate(process: DisplayableProcess): ValidationResult
}