package pl.touk.nussknacker.engine.definition.validator

import javax.validation.constraints.NotBlank
import pl.touk.nussknacker.engine.api.definition.{NotBlankParameterValidator, ParameterValidator}

object ValidatorsExtractor {
  def extract(params: ValidatorExtractorParameters): List[ParameterValidator] = {
    List(
      MandatoryValidatorExtractor,
      AnnotationValidatorExtractor[NotBlank](NotBlankParameterValidator),
      FixedValueValidatorExtractor,
      LiteralValidatorExtractor,
      SingleValueAnnotationValidatorExtractor
    ).flatMap(_.extract(params))
  }

}
