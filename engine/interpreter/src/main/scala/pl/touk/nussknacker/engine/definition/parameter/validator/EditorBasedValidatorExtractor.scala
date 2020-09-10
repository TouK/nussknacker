package pl.touk.nussknacker.engine.definition.parameter.validator

import pl.touk.nussknacker.engine.api.definition._
import pl.touk.nussknacker.engine.definition.parameter.editor.EditorExtractor

object EditorBasedValidatorExtractor extends ValidatorExtractor {

  override def extract(params: ValidatorExtractorParameters): Option[ParameterValidator] = {
    EditorExtractor.extract(params.parameterData, params.parameterConfig) match {
      case Some(FixedValuesParameterEditor(possibleValues)) => Some(FixedValuesValidator(possibleValues))
      case Some(JsonParameterEditor) => Some(JsonValidator)
      case _ => None
    }
  }
}
