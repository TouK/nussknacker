package pl.touk.nussknacker.engine.definition.validator

import java.lang.reflect.Parameter

import pl.touk.nussknacker.engine.api.definition.{ParameterEditor, ParameterValidator}

case class ValidatorsExtractor(possibleEditor: Option[ParameterEditor]) {
  def extract(parameter: Parameter): List[ParameterValidator] = {
    List(
      MandatoryValueValidatorExtractor,
      AnnotationValidatorExtractor[NotBlank](NotBlankParameterValidator),
      FixedValueValidatorExtractor(possibleEditor)
    ).flatMap(_.extract(parameter))
  }
}
