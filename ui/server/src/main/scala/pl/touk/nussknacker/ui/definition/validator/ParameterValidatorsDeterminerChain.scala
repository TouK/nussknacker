package pl.touk.nussknacker.ui.definition.validator

import pl.touk.nussknacker.engine.api.definition.{Parameter, ParameterValidator}
import pl.touk.nussknacker.engine.api.process.ParameterConfig

object ParameterValidatorsDeterminerChain {

  /**
   * Caution! File configuration completely overwrites any other validators configuration
   */
  def apply(parameterConfig: ParameterConfig): ParameterValidatorsDeterminerChain = {
    val strategies = Seq(
      new ParameterConfigValidatorsDeterminer(parameterConfig),
      new ParameterBasedValidatorsDeterminer
    )
    new ParameterValidatorsDeterminerChain(strategies)
  }
}

class ParameterValidatorsDeterminerChain(strategies: Iterable[ParameterValidatorsDeterminer]) {

  def determineValidators(param: Parameter): List[ParameterValidator] = {
    strategies.view
      .flatMap(_.determineParameterValidators(param))
      .headOption
      .getOrElse(List.empty)
  }
}
