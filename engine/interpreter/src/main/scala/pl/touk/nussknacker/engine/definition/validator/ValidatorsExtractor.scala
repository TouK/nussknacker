package pl.touk.nussknacker.engine.definition.validator

import java.lang.reflect.Parameter

import javax.validation.constraints.NotBlank
import pl.touk.nussknacker.engine.api.definition.{NotBlankParameterValidator, ParameterEditor, ParameterValidator}

case class ValidatorsExtractor(possibleEditor: Option[ParameterEditor]) {
  def extract(parameter: Parameter): List[ParameterValidator] = {
    List(
      MandatoryValidatorExtractor,
      AnnotationValidatorExtractor[NotBlank](NotBlankParameterValidator),
      FixedValueValidatorExtractor(possibleEditor)
    ).flatMap(_.extract(parameter))
  }
}
