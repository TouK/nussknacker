package pl.touk.nussknacker.engine.definition.validator

import java.lang.reflect.Parameter

import javax.validation.constraints.NotBlank
import pl.touk.nussknacker.engine.api.definition.{LiteralIntValidator, NotBlankParameterValidator, ParameterEditor, ParameterValidator}
import pl.touk.nussknacker.engine.api.validation.LiteralType

case class ValidatorsExtractor(possibleEditor: Option[ParameterEditor]) {
  def extract(parameter: Parameter): List[ParameterValidator] = {
    List(
      MandatoryValidatorExtractor,
      AnnotationValidatorExtractor[NotBlank](NotBlankParameterValidator),
      FixedValueValidatorExtractor(possibleEditor),
      LiteralValidatorExtractor(LiteralType.Integer, LiteralIntValidator)
    ).flatMap(_.extract(parameter))
  }
}
