package pl.touk.nussknacker.ui.process.label

import cats.data.Validated
import pl.touk.nussknacker.ui.BadRequestError
import pl.touk.nussknacker.ui.api.ScenarioLabelSettings

final case class ValidatedScenarioLabel private (value: String)

object ValidatedScenarioLabel {

  def create(
      label: String,
      scenarioLabelSettings: Option[ScenarioLabelSettings]
  ): Validated[LabelValidationError, ValidatedScenarioLabel] = {
    scenarioLabelSettings match {
      case Some(settings) =>
        Validated.cond(
          label.matches(settings.validationPattern),
          new ValidatedScenarioLabel(label),
          LabelValidationError(label, settings)
        )
      case None =>
        Validated.valid(new ValidatedScenarioLabel(label))
    }
  }

  final case class LabelValidationError(message: String) extends BadRequestError(message)

  object LabelValidationError {

    def apply(label: String, scenarioLabelSettings: ScenarioLabelSettings): LabelValidationError = {
      new LabelValidationError(
        s"Bad scenario label format '$label'. Validation pattern: ${scenarioLabelSettings.validationPattern}"
      )
    }

  }

}
