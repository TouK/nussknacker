package pl.touk.nussknacker.ui.definition.additionalproperty

import pl.touk.nussknacker.engine.api.definition.ParameterValidator

trait AdditionalPropertyValidatorDeterminer {

  def determine(): Option[List[ParameterValidator]]
}
