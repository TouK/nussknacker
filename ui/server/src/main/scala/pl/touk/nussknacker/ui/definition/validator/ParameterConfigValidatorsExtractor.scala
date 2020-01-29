package pl.touk.nussknacker.ui.definition.validator

import pl.touk.nussknacker.engine.api.definition.{Parameter, ParameterValidator}
import pl.touk.nussknacker.engine.api.process.ParameterConfig

protected class ParameterConfigValidatorsExtractor(config: ParameterConfig) extends ParameterValidatorsExtractorStrategy {

  override def extract(param: Parameter): Option[List[ParameterValidator]] = config.validators

}
