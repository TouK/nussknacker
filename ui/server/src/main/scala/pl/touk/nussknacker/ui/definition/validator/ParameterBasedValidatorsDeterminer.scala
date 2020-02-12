package pl.touk.nussknacker.ui.definition.validator
import pl.touk.nussknacker.engine.api.definition.{Parameter, ParameterValidator}

protected class ParameterBasedValidatorsDeterminer extends ParameterValidatorsDeterminer {

  override def determineParameterValidators(param: Parameter): Option[List[ParameterValidator]] = Some(param.validators)

}
