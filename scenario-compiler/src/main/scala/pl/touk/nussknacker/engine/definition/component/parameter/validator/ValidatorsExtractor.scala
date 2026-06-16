package pl.touk.nussknacker.engine.definition.component.parameter.validator

import pl.touk.nussknacker.engine.api.definition
import pl.touk.nussknacker.engine.api.definition.{
  CustomParameterValidatorByClassLoader,
  MaximalNumberValidator,
  MinimalNumberValidator,
  NotBlankValidator,
  NotEmptyCollectionValidator,
  NotNullValidator,
  ParameterValidator
}
import pl.touk.nussknacker.engine.api.validation.{CustomValidator, JsonValidator}

import javax.validation.constraints.{Max, Min, NotBlank, NotEmpty, NotNull}

object ValidatorsExtractor {

  def extract(params: ValidatorExtractorParameters): List[ParameterValidator] = {
    val fromValidatorExtractors = List(
      MandatoryValidatorExtractor,
      EditorBasedValidatorExtractor,
      AnnotationValidatorExtractor[JsonValidator](definition.JsonExpressionValidator),
      CompileTimeEvaluableValueValidatorExtractor,
      AnnotationValidatorExtractor[NotBlank](NotBlankValidator),
      AnnotationValidatorExtractor[NotNull](NotNullValidator),
      // @NotEmpty supports only collections (java.util.Collection / java.util.Map); for strings use @NotBlank
      AnnotationValidatorExtractor[NotEmpty](NotEmptyCollectionValidator),
      AnnotationValidatorExtractor[Min]((annotation: Min) => MinimalNumberValidator(annotation.value())),
      AnnotationValidatorExtractor[Max]((annotation: Max) => MaximalNumberValidator(annotation.value())),
      AnnotationValidatorExtractor[CustomValidator]((annotation: CustomValidator) =>
        CustomParameterValidatorByClassLoader(annotation.value())
      )
    ).flatMap(_.extract(params))
    // TODO: should validators from config override or append those from annotations, types etc.?
    (fromValidatorExtractors ++ params.parameterConfig.validators.toList.flatten).distinct
  }

}
