package pl.touk.nussknacker.engine.process

import com.typesafe.config.Config
import pl.touk.nussknacker.engine.{CustomProcessValidator, CustomProcessValidatorFactory}
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.ScenarioNameValidationError
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess

import scala.concurrent.Future

class FlinkScenarioNameValidatorFactory extends CustomProcessValidatorFactory {
  override def validator(config: Config): CustomProcessValidator = new FlinkScenarioNameValidator(config)
}

class FlinkScenarioNameValidator(config: Config) extends CustomProcessValidator {

  private lazy val flinkProcessNameValidationPattern = "[a-zA-Z0-9-_ ]+"r

  def validate(process: CanonicalProcess): List[ScenarioNameValidationError] = {

    val scenarioName = process.metaData.id
    if (flinkProcessNameValidationPattern.pattern.matcher(scenarioName).matches()) {
      List()
    } else {
      List(
        ScenarioNameValidationError(scenarioName, "Allowed characters include numbers letters, underscores(_), hyphens(-) and spaces")
      )
    }
  }

}
