package pl.touk.nussknacker.engine.definition.parameter.validator

import pl.touk.nussknacker.engine.api.definition._

object EditorBasedValidatorExtractor extends ValidatorExtractor {

  override def extract(params: ValidatorExtractorParameters): Option[ParameterValidator] = {
    params.extractedEditor.collect {
      case FixedValuesParameterEditor(possibleValues) => FixedValuesValidator(possibleValues)
      case FixedValuesPresetParameterEditor(_, possibleValues) if possibleValues.nonEmpty =>
        // during validation preset resolution failures are caught earlier
        // otherwise, we let it slide to for example not block deployment
        FixedValuesValidator(possibleValues)
    }
  }

}
