package pl.touk.nussknacker.ui.definition.validator

import pl.touk.nussknacker.engine.api.definition.{Parameter, ParameterValidator}
import pl.touk.nussknacker.engine.api.process.ParameterConfig

protected class ParameterConfigValidatorsDeterminer(config: ParameterConfig) extends ParameterValidatorsDeterminer {

  override def determineParameterValidators(param: Parameter): Option[List[ParameterValidator]] = config.validators

}
