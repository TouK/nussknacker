package pl.touk.nussknacker.ui.definition.validator

import pl.touk.nussknacker.engine.api.definition.{Parameter, ParameterValidator}

trait ParameterValidatorsDeterminer {

  def determineParameterValidators(param: Parameter): Option[List[ParameterValidator]]

}
