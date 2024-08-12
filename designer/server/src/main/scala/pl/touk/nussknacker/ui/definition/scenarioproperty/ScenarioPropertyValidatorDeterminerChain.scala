package pl.touk.nussknacker.ui.definition.scenarioproperty

import pl.touk.nussknacker.engine.api.definition.ParameterValidator
import pl.touk.nussknacker.engine.api.component.ScenarioPropertiesParameterConfig

object ScenarioPropertyValidatorDeterminerChain {

  def apply(config: ScenarioPropertiesParameterConfig): ScenarioPropertyValidatorsDeterminerChain = {
    val strategies = Seq(
      new ScenarioPropertyConfigValidatorDeterminer(config),
      new ScenarioPropertyEditorValidatorDeterminer(config.editor)
    )
    new ScenarioPropertyValidatorsDeterminerChain(strategies)
  }

}

class ScenarioPropertyValidatorsDeterminerChain(strategies: Iterable[ScenarioPropertyValidatorDeterminer]) {

  def determine(): List[ParameterValidator] = {
    strategies.view
      .flatMap(_.determine())
      .headOption
      .getOrElse(List.empty)
  }

}
