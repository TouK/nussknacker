package pl.touk.nussknacker.engine.definition.component.parameter.validator

import pl.touk.nussknacker.engine.api.definition._

object EditorBasedValidatorExtractor extends ValidatorExtractor {

  override def extract(params: ValidatorExtractorParameters): Option[ParameterValidator] = {
    params.extractedEditor.collect { case FixedValuesParameterEditor(possibleValues, _) =>
      FixedValuesValidator(possibleValues)
    }
  }

}
