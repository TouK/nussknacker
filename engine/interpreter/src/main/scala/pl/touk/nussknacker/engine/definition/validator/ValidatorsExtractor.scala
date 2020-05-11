package pl.touk.nussknacker.engine.definition.validator

import javax.validation.constraints.{Max, Min, NotBlank}
import pl.touk.nussknacker.engine.api.definition.{MaximalNumberValidator, MinimalNumberValidator, NotBlankParameterValidator, ParameterValidator}

object ValidatorsExtractor {
  def extract(params: ValidatorExtractorParameters): List[ParameterValidator] = {
    List(
      MandatoryValidatorExtractor,
      AnnotationValidatorExtractor[NotBlank](_ => NotBlankParameterValidator),
      FixedValueValidatorExtractor,
      LiteralValidatorExtractor,
      AnnotationValidatorExtractor[Min](annotation => MinimalNumberValidator(annotation.value())),
      AnnotationValidatorExtractor[Max](annotation => MaximalNumberValidator(annotation.value()))
    ).flatMap(_.extract(params))
  }
}
