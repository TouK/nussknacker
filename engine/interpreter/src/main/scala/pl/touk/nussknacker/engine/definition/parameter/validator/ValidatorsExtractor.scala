package pl.touk.nussknacker.engine.definition.parameter.validator

import javax.validation.constraints.{Max, Min, NotBlank}
import pl.touk.nussknacker.engine.api.definition.{MaximalNumberValidator, MinimalNumberValidator, NotBlankParameterValidator, ParameterValidator}

object ValidatorsExtractor {
  def extract(params: ValidatorExtractorParameters): List[ParameterValidator] = {
    val fromValidatorExtractors = List(
      MandatoryValidatorExtractor,
      EditorBasedValidatorExtractor,
      LiteralValidatorExtractor,
      AnnotationValidatorExtractor[NotBlank](NotBlankParameterValidator),
      AnnotationValidatorExtractor[Min]((annotation: Min) => MinimalNumberValidator(annotation.value())),
      AnnotationValidatorExtractor[Max]((annotation: Max) => MaximalNumberValidator(annotation.value()))
    ).flatMap(_.extract(params))
    //TODO: should validators from config override or append those from annotations, types etc.?
    (fromValidatorExtractors ++ params.parameterConfig.validators.toList.flatten).distinct
  }
}
