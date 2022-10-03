package pl.touk.nussknacker.ui.definition.additionalproperty

import pl.touk.nussknacker.engine.api.definition.ParameterValidator
import pl.touk.nussknacker.engine.api.component.AdditionalPropertyConfig

object AdditionalPropertyValidatorDeterminerChain {

  def apply(config: AdditionalPropertyConfig): AdditionalPropertyValidatorsDeterminerChain = {
    val strategies = Seq(
      new AdditionalPropertyConfigValidatorDeterminer(config),
      new AdditionalPropertyEditorValidatorDeterminer(config.editor)
    )
    new AdditionalPropertyValidatorsDeterminerChain(strategies)
  }
}

class AdditionalPropertyValidatorsDeterminerChain(strategies: Iterable[AdditionalPropertyValidatorDeterminer]) {

  def determine(): List[ParameterValidator] = {
    strategies.view
      .flatMap(_.determine())
      .headOption
      .getOrElse(List.empty)
  }
}
