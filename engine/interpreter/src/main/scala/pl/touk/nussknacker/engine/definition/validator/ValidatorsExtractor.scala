package pl.touk.nussknacker.engine.definition.validator

import javax.validation.constraints.{Max, Min, NotBlank}
import pl.touk.nussknacker.engine.api.definition.{MaximalNumberValidator, MinimalNumberValidator, NotBlankParameterValidator, ParameterValidator}

object ValidatorsExtractor {
  def extract(params: ValidatorExtractorParameters): List[ParameterValidator] = {
    List(
      MandatoryValidatorExtractor,
      AnnotationValidatorExtractor[NotBlank](NotBlankParameterValidator),
      FixedValueValidatorExtractor,
      LiteralValidatorExtractor,
      AnnotationValidatorExtractor[Min]((annotation: Min) => MinimalNumberValidator(annotation.value())),
      AnnotationValidatorExtractor[Max]((annotation: Max) => MaximalNumberValidator(annotation.value())),
      JsonValidatorExtractor
    ).flatMap(_.extract(params))
  }
}
