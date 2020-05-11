package pl.touk.nussknacker.engine.definition.validator

import pl.touk.nussknacker.engine.api.definition.{FixedValuesParameterEditor, FixedValuesValidator, ParameterValidator}

object FixedValueValidatorExtractor extends ValidatorExtractor {

  override def extract(params: ValidatorExtractorParameters): Option[ParameterValidator] = {
    params.extractedEditor match {
      case Some(FixedValuesParameterEditor(possibleValues)) => Some(FixedValuesValidator(possibleValues))
      case _ => None
    }
  }
}
