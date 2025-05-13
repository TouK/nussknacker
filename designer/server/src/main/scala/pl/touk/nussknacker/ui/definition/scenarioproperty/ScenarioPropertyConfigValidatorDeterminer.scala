package pl.touk.nussknacker.ui.definition.scenarioproperty

import pl.touk.nussknacker.engine.api.component.ScenarioPropertyConfig
import pl.touk.nussknacker.engine.api.definition.ParameterValidator

protected class ScenarioPropertyConfigValidatorDeterminer(config: ScenarioPropertyConfig)
    extends ScenarioPropertyValidatorDeterminer {

  override def determine(): Option[List[ParameterValidator]] = config.validators
}
