package pl.touk.nussknacker.engine.definition.parameter.validator

import pl.touk.nussknacker.engine.api.definition._

object EditorBasedValidatorExtractor extends ValidatorExtractor {

  override def extract(params: ValidatorExtractorParameters): Option[ParameterValidator] = {
    params.extractedEditor.flatMap {
      case FixedValuesParameterEditor(possibleValues) => Some(FixedValuesValidator(possibleValues))
      case JsonParameterEditor => Some(JsonValidator)
      case _ => None
    }
  }

}
