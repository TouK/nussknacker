package pl.touk.nussknacker.engine.definition.parameter.validator

import pl.touk.nussknacker.engine.api.definition._

object EditorBasedValidatorExtractor extends ValidatorExtractor {

  override def extract(params: ValidatorExtractorParameters): Option[ParameterValidator] = {
    params.extractedEditor.collect {
      case FixedValuesParameterEditor(possibleValues)                => FixedValuesValidator(possibleValues)
      case FixedValuesPresetParameterEditor(_, Some(possibleValues)) =>
        // Some(possibleValues) because preset resolution failures are caught earlier, in UIProcessResolving
        FixedValuesValidator(possibleValues)
    }
  }

}
