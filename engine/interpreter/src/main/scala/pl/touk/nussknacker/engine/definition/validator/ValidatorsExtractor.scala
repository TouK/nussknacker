package pl.touk.nussknacker.engine.definition.validator

import java.lang.reflect.Parameter

import pl.touk.nussknacker.engine.api.definition.ParameterValidator

object ValidatorsExtractor {
  val validators = List(
    MandatoryValueValidatorExtractor,
    NotBlankValueValidatorExtractor
  )

  def extract(parameter: Parameter): List[ParameterValidator] =
    validators.flatMap(_.extract(parameter).toList)
}
