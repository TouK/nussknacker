package pl.touk.nussknacker.engine.definition.validator

import java.lang.reflect.Parameter

import javax.validation.constraints.NotBlank
import pl.touk.nussknacker.engine.api.definition.{LiteralIntValidator, NotBlankParameterValidator, ParameterEditor, ParameterValidator}
import pl.touk.nussknacker.engine.api.validation.LiteralInt

case class ValidatorsExtractor(possibleEditor: Option[ParameterEditor]) {
  def extract(parameter: Parameter): List[ParameterValidator] = {
    List(
      MandatoryValidatorExtractor,
      AnnotationValidatorExtractor[NotBlank](NotBlankParameterValidator),
      AnnotationValidatorExtractor[LiteralInt](LiteralIntValidator),
      FixedValueValidatorExtractor(possibleEditor)
    ).flatMap(_.extract(parameter))
  }
}
