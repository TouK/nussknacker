package pl.touk.nussknacker.engine.definition.validator

import pl.touk.nussknacker.engine.api.definition.{JsonValidator, ParameterValidator}

object JsonValidatorExtractor extends ValidatorExtractor {

  override def extract(params: ValidatorExtractorParameters): Option[ParameterValidator] = {
    params.extractedEditor match {
      // TODO add json editor case
      // case _ => Some(JsonValidator)
      case _ => None
    }
  }
}
