package pl.touk.nussknacker.ui.definition.validator

import pl.touk.nussknacker.engine.api.definition.{Parameter, ParameterValidator}

trait ParameterValidatorsExtractorStrategy {

  def extract(param: Parameter): Option[List[ParameterValidator]]

}
