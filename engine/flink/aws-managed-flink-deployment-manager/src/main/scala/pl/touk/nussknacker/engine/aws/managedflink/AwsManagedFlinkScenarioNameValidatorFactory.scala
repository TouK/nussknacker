package pl.touk.nussknacker.engine.aws.managedflink

import cats.data.{NonEmptyList, ValidatedNel}
import cats.data.Validated.{Invalid, Valid}
import com.typesafe.config.Config
import pl.touk.nussknacker.engine.{CustomProcessValidator, CustomProcessValidatorFactory}
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.ScenarioNameValidationError
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess

class AwsManagedFlinkScenarioNameValidatorFactory extends CustomProcessValidatorFactory {
  override def validator(config: Config): CustomProcessValidator = new AwsManagedFlinkScenarioNameValidator(config)
}

class AwsManagedFlinkScenarioNameValidator(config: Config) extends CustomProcessValidator {

  def validate(process: CanonicalProcess): ValidatedNel[ScenarioNameValidationError, Unit] = {
    val scenarioName = process.name
    if (FlinkApplicationName.validationPattern.pattern.matcher(scenarioName.value).matches()) {
      Valid(())
    } else {
      Invalid(
        NonEmptyList.one(
          ScenarioNameValidationError(
            s"Invalid scenario name: '$scenarioName'. Only digits, letters, hyphen (-) and space in the middle are allowed",
            "Provided scenario name is invalid. Please enter valid name using only specified characters."
          )
        )
      )
    }
  }

}
