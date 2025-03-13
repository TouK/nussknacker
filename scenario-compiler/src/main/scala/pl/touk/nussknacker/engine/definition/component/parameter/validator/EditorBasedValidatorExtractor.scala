package pl.touk.nussknacker.engine.definition.component.parameter.validator

import pl.touk.nussknacker.engine.api.definition._

object EditorBasedValidatorExtractor extends ValidatorExtractor {

  override def extract(params: ValidatorExtractorParameters): Option[ParameterValidator] = {
    params.extractedEditors match {
      case Some(ParameterEditors(FixedValuesParameterEditor(possibleValues), None)) =>
        Some(FixedValuesValidator(possibleValues))
      case _ => None
    }
  }

}
