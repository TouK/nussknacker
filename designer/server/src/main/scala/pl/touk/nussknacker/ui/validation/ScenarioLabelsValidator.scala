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
      .traverse(label => validateScenarioLabel(validationRules, label).toValidatedNel)
      .andThen(labels => validateUniqueness(labels))
  }

  private def getValidationRules =
    config
      .flatMap { settings =>
        NonEmptyList.fromList(settings.validationRules)
      }

  private def validateScenarioLabel(
      validationRules: NonEmptyList[ScenarioLabelConfig.ValidationRule],
      label: ScenarioLabel
  ): Validated[ValidationError, ScenarioLabel] = {
    validationRules
      .traverse(rule => validate(label, rule))
      .as(label)
      .leftMap(messages => ValidationError(label.value, messages))
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

  private def validateUniqueness(labels: NonEmptyList[ScenarioLabel]) = {
    labels.toList
      .groupBy(identity)
      .filter { case (_, values) =>
        values.length > 1
      }
      .keys
      .toList
      .toNel match {
      case Some(notUniqueLabels) =>
        Validated.invalid(
          notUniqueLabels
            .map(label => ValidationError(label.value, NonEmptyList.one("Label has to be unique")))
        )
      case None =>
        Validated.validNel(())
    }
  }

}

object ScenarioLabelsValidator {

  final case class ValidationError(label: String, validationMessages: NonEmptyList[String])
}
