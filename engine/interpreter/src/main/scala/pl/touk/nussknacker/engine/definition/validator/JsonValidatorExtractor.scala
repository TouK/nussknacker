package pl.touk.nussknacker.engine.definition.validator

import pl.touk.nussknacker.engine.api.definition.{JsonParameterEditor, JsonValidator, ParameterValidator}

object JsonValidatorExtractor extends ValidatorExtractor {

  override def extract(params: ValidatorExtractorParameters): Option[ParameterValidator] = {
    params.extractedEditor match {
      case Some(JsonParameterEditor) => Some(JsonValidator)
      case _ => None
    }
  }
}
