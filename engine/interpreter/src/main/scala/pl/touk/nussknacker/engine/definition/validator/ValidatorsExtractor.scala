package pl.touk.nussknacker.engine.definition.validator

import java.lang.reflect.Parameter

import javax.validation.constraints.NotBlank
import pl.touk.nussknacker.engine.api.definition.{NotBlankParameterValidator, ParameterValidator}

object ValidatorsExtractor {
  val validators = List(
    MandatoryValidatorExtractor,
    AnnotationValidatorExtractor[NotBlank](NotBlankParameterValidator)
  )

  def extract(parameter: Parameter): List[ParameterValidator] =
    validators.flatMap(_.extract(parameter).toList)
}
