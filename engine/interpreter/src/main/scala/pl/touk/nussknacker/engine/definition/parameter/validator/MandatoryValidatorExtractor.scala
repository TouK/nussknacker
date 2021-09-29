package pl.touk.nussknacker.engine.definition.parameter.validator

import javax.annotation.Nullable
import pl.touk.nussknacker.engine.api.definition.{MandatoryParameterValidator, ParameterValidator}

object MandatoryValidatorExtractor extends ValidatorExtractor {

  override def extract(params: ValidatorExtractorParameters): Option[ParameterValidator] = {
    if (params.isOptional) {
      None
    } else {
      Some(MandatoryParameterValidator)
    }
  }

}
