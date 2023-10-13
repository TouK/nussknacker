package pl.touk.nussknacker.ui.definition.scenarioproperty

import pl.touk.nussknacker.engine.api.definition.ParameterValidator

trait ScenarioPropertyValidatorDeterminer {

  def determine(): Option[List[ParameterValidator]]
}
