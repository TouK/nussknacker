package pl.touk.nussknacker.engine.definition.component.parameter.validator

import pl.touk.nussknacker.engine.api.definition
import pl.touk.nussknacker.engine.api.definition.{
  CompileTimeNonNegativeDurationValidator,
  CompileTimePositiveDurationValidator,
  CustomParameterValidatorByClassLoader,
  MaximalNumberValidator,
  MinimalNumberValidator,
  NonNegativeDurationValidator,
  NotBlankParameterValidator,
  ParameterValidator,
  PositiveDurationValidator
}
import pl.touk.nussknacker.engine.api.validation.{
  CustomValidator,
  JsonValidator,
  NonNegativeDuration,
  PositiveDuration,
  ValidatorMode
}

import javax.validation.constraints.{Max, Min, NotBlank}

object ValidatorsExtractor {

  private val NonNegativeDurationSupportedValidationModes: Map[ValidatorMode, ParameterValidator] = Map(
    ValidatorMode.COMPILE_TIME             -> CompileTimeNonNegativeDurationValidator,
    ValidatorMode.COMPILE_TIME_AND_RUNTIME -> NonNegativeDurationValidator
  )

  private val PositiveDurationSupportedValidationModes: Map[ValidatorMode, ParameterValidator] = Map(
    ValidatorMode.COMPILE_TIME             -> CompileTimePositiveDurationValidator,
    ValidatorMode.COMPILE_TIME_AND_RUNTIME -> PositiveDurationValidator
  )

  def extract(params: ValidatorExtractorParameters): List[ParameterValidator] = {
    val fromValidatorExtractors = List(
      MandatoryValidatorExtractor,
      EditorBasedValidatorExtractor,
      AnnotationValidatorExtractor[JsonValidator](definition.JsonValidator),
      CompileTimeEvaluableValueValidatorExtractor,
      AnnotationValidatorExtractor[NotBlank](NotBlankParameterValidator),
      AnnotationValidatorExtractor[Min]((annotation: Min) => MinimalNumberValidator(annotation.value())),
      AnnotationValidatorExtractor[Max]((annotation: Max) => MaximalNumberValidator(annotation.value())),
      AnnotationValidatorExtractor[NonNegativeDuration]((annotation: NonNegativeDuration) =>
        validatorForMode(
          params,
          annotation.annotationType(),
          annotation.mode(),
          NonNegativeDurationSupportedValidationModes
        )
      ),
      AnnotationValidatorExtractor[PositiveDuration]((annotation: PositiveDuration) =>
        validatorForMode(
          params,
          annotation.annotationType(),
          annotation.mode(),
          PositiveDurationSupportedValidationModes
        )
      ),
      AnnotationValidatorExtractor[CustomValidator]((annotation: CustomValidator) =>
        CustomParameterValidatorByClassLoader(annotation.value())
      )
    ).flatMap(_.extract(params))
    // TODO: should validators from config override or append those from annotations, types etc.?
    (fromValidatorExtractors ++ params.parameterConfig.validators.toList.flatten).distinct
  }

  private[validator] def validatorForMode(
      params: ValidatorExtractorParameters,
      annotationClass: Class[_],
      mode: ValidatorMode,
      supportedValidationModes: Map[ValidatorMode, ParameterValidator]
  ): ParameterValidator = {
    val resolvedMode = mode match {
      case ValidatorMode.AUTO if params.parameterData.isLazyParameter => ValidatorMode.COMPILE_TIME_AND_RUNTIME
      case ValidatorMode.AUTO                                         => ValidatorMode.COMPILE_TIME
      case explicitMode                                               => explicitMode
    }

    supportedValidationModes.getOrElse(
      resolvedMode, {
        val supportedModes = supportedValidationModes.keys.toList.map(_.toString).sorted.mkString(", ")
        throw new IllegalArgumentException(
          s"@${annotationClass.getSimpleName} does not support the $resolvedMode mode. Supported modes: $supportedModes."
        )
      }
    )
  }

}
