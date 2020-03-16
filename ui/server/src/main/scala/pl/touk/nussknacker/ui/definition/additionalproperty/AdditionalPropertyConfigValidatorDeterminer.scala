package pl.touk.nussknacker.ui.definition.additionalproperty

import pl.touk.nussknacker.engine.api.definition.ParameterValidator
import pl.touk.nussknacker.engine.api.process.AdditionalPropertyConfig

protected class AdditionalPropertyConfigValidatorDeterminer(config: AdditionalPropertyConfig) extends AdditionalPropertyValidatorDeterminer {

  override def determine(): Option[List[ParameterValidator]] = config.validators
}
