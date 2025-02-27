package pl.touk.nussknacker.engine.process

import cats.data.{NonEmptyList, ValidatedNel}
import cats.data.Validated.{Invalid, Valid}
import com.typesafe.config.Config
import pl.touk.nussknacker.engine.{CustomProcessValidator, CustomProcessValidatorFactory}
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.ScenarioNameValidationError
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess

class FlinkScenarioNameValidatorFactory extends CustomProcessValidatorFactory {
  override def validator(config: Config): CustomProcessValidator = new FlinkScenarioNameValidator(config)
}

class FlinkScenarioNameValidator(config: Config) extends CustomProcessValidator {

  private val flinkProcessNameValidationPattern = "[a-zA-Z0-9_-]++[a-zA-Z0-9_ -]*+(?<! )".r

  def validate(process: CanonicalProcess): ValidatedNel[ScenarioNameValidationError, Unit] = {
    val scenarioName = process.name
    if (flinkProcessNameValidationPattern.pattern.matcher(scenarioName.value).matches()) {
      Valid(())
    } else {
      Invalid(
        NonEmptyList.one(
          ScenarioNameValidationError(
            s"Invalid scenario name $scenarioName. Only digits, letters, underscore (_), hyphen (-) and space in the middle are allowed",
            "Provided scenario name is invalid for this category. Please enter valid name using only specified characters."
          )
        )
      )
    }
  }

}
