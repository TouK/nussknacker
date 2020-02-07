package pl.touk.nussknacker.engine.definition.validator

import java.lang.reflect.Parameter

import pl.touk.nussknacker.engine.api.definition.ParameterValidator

trait ValidatorExtractor {

  def extract(p: Parameter): Option[ParameterValidator]

}
