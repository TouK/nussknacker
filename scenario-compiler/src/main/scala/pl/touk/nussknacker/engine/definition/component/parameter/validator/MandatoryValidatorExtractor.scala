package pl.touk.nussknacker.engine.definition.component.parameter.validator

import pl.touk.nussknacker.engine.api.definition.{MandatoryExpressionValidator, ParameterValidator}

object MandatoryValidatorExtractor extends ValidatorExtractor {

  override def extract(params: ValidatorExtractorParameters): Option[ParameterValidator] = {
    if (params.isOptional) {
      None
    } else {
      Some(MandatoryExpressionValidator)
    }
  }

}
