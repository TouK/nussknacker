package pl.touk.nussknacker.ui.definition.scenarioproperty

import pl.touk.nussknacker.engine.api.definition.ParameterValidator
import pl.touk.nussknacker.engine.api.component.ScenarioPropertyConfig

protected class ScenarioPropertyConfigValidatorDeterminer(config: ScenarioPropertyConfig)
    extends ScenarioPropertyValidatorDeterminer {

  override def determine(): Option[List[ParameterValidator]] = config.validators
}
