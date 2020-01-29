package pl.touk.nussknacker.ui.definition.validator

import pl.touk.nussknacker.engine.api.definition.{Parameter, ParameterValidator}
import pl.touk.nussknacker.engine.api.process.ParameterConfig

object ParameterValidatorsExtractorChain {

  def apply(parameterConfig: ParameterConfig): ParameterValidatorsExtractorChain = {
    val strategies = Seq(
      new ParameterConfigValidatorsExtractor(parameterConfig),
      new ParameterValidatorsExtractor
    )
    new ParameterValidatorsExtractorChain(strategies)
  }
}

class ParameterValidatorsExtractorChain(strategies: Iterable[ParameterValidatorsExtractorStrategy]) {

  def evaluate(param: Parameter): List[ParameterValidator] = {
    strategies.view
      .flatMap(_.extract(param))
      .headOption
      .getOrElse(List.empty)
  }
}
