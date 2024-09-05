package pl.touk.nussknacker.ui.validation

import cats.data.{NonEmptyList, Validated, ValidatedNel}
import cats.implicits._
import pl.touk.nussknacker.ui.config.ScenarioLabelConfig
import pl.touk.nussknacker.ui.process.label.ScenarioLabel
import pl.touk.nussknacker.ui.validation.ScenarioLabelsValidator.ValidationError

class ScenarioLabelsValidator(config: Option[ScenarioLabelConfig]) {

  def validate(labels: List[ScenarioLabel]): ValidatedNel[ValidationError, Unit] = {
    (for {
      validationRules <- getValidationRules
      scenarioLabels  <- NonEmptyList.fromList(labels)
      result = validateLabels(scenarioLabels, validationRules)
    } yield result).getOrElse(Validated.validNel(()))
  }

  private def validateLabels(
      scenarioLabels: NonEmptyList[ScenarioLabel],
      validationRules: NonEmptyList[ScenarioLabelConfig.ValidationRule]
  ): Validated[NonEmptyList[ValidationError], Unit] = {
    scenarioLabels
      .map(label => validateScenarioLabel(validationRules, label).toValidatedNel)
      .sequence
      .map((_: NonEmptyList[Unit]) => ())
  }

  private def getValidationRules =
    config
      .flatMap { settings =>
        NonEmptyList.fromList(settings.validationRules)
      }

  private def validateScenarioLabel(
      validationRules: NonEmptyList[ScenarioLabelConfig.ValidationRule],
      label: ScenarioLabel
  ): Validated[ValidationError, Unit] = {
    validationRules
      .map(rule => validate(label, rule))
      .sequence
      .leftMap(messages => ValidationError(label.value, messages))
      .map((_: NonEmptyList[Unit]) => ())
  }

  private def validate(label: ScenarioLabel, rule: ScenarioLabelConfig.ValidationRule) = {
    Validated
      .cond(
        // in scala 2.13 we can use `matches`, but there is no such method in scala 2.12
        rule.validationRegex.unapplySeq(label.value).isDefined,
        (),
        rule.messageWithLabel(label.value)
      )
      .toValidatedNel
  }

}

object ScenarioLabelsValidator {

  val default: ScenarioLabelsValidator = new ScenarioLabelsValidator(None)

  final case class ValidationError(label: String, validationMessages: NonEmptyList[String])
}
