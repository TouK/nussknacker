package pl.touk.nussknacker.ui.validation

import cats.data.{NonEmptyList, Validated, ValidatedNel}
import cats.implicits._
import pl.touk.nussknacker.ui.api.ScenarioLabelSettings
import pl.touk.nussknacker.ui.process.label.ScenarioLabel
import pl.touk.nussknacker.ui.validation.ScenarioLabelsValidator.ValidationError

class ScenarioLabelsValidator(scenarioLabelSettings: Option[ScenarioLabelSettings]) {

  def validate(labels: List[ScenarioLabel]): ValidatedNel[ValidationError, Unit] = {
    (for {
      validationRules <- getValidationRules
      scenarioLabels  <- NonEmptyList.fromList(labels)
      result = scenarioLabels
        .flatMap(label => validationRules.map(rule => validate(label, rule)))
        .sequence
        .map((_: NonEmptyList[Unit]) => ())
    } yield result).getOrElse(Validated.validNel(()))
  }

  private def getValidationRules =
    scenarioLabelSettings
      .flatMap { settings =>
        NonEmptyList.fromList(settings.validationRules)
      }

  private def validate(label: ScenarioLabel, rule: ScenarioLabelSettings.ValidationRule) = {
    Validated
      .cond(
        rule.validationRegex.matches(label.value),
        (),
        ValidationError(
          label.value,
          rule.message
        )
      )
      .toValidatedNel
  }

}

object ScenarioLabelsValidator {
  final case class ValidationError(label: String, validationMessage: String)
}
