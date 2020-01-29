package pl.touk.nussknacker.ui.definition.validator
import pl.touk.nussknacker.engine.api.definition.{Parameter, ParameterValidator}

protected class ParameterValidatorsExtractor extends ParameterValidatorsExtractorStrategy {

  override def extract(param: Parameter): Option[List[ParameterValidator]] = param.validators

}
