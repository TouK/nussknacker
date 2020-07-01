package pl.touk.nussknacker.engine.definition.validator

import pl.touk.nussknacker.engine.api.definition.{FixedValuesParameterEditor, FixedValuesValidator, JsonParameterEditor, JsonValidator, ParameterValidator}

object EditorBasedValidatorExtractor extends ValidatorExtractor {

  override def extract(params: ValidatorExtractorParameters): Option[ParameterValidator] = {
    params.extractedEditor match {
      case Some(FixedValuesParameterEditor(possibleValues)) => Some(FixedValuesValidator(possibleValues))
      case Some(JsonParameterEditor) => Some(JsonValidator)
      case _ => None
    }
  }
}
