package pl.touk.nussknacker.ui.definition.scenarioproperty

import pl.touk.nussknacker.engine.api.definition.ParameterValidator
import pl.touk.nussknacker.engine.api.component.ScenarioPropertiesParameterConfig

protected class ScenarioPropertyConfigValidatorDeterminer(config: ScenarioPropertiesParameterConfig)
    extends ScenarioPropertyValidatorDeterminer {

  override def determine(): Option[List[ParameterValidator]] = config.validators
}
