package pl.touk.nussknacker.engine.definition.validator

import java.lang.reflect.Parameter

import pl.touk.nussknacker.engine.api.definition.ParameterValidator

object ValidatorsExtractor {
  def extract(parameter: Parameter): List[ParameterValidator] = {
    List(MandatoryValueValidatorExtractor).flatMap(_.extract(parameter)).toList
  }
}
