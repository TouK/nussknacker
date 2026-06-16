package pl.touk.nussknacker.engine.definition.component.parameter.validator

import pl.touk.nussknacker.engine.api.definition._

object EditorBasedValidatorExtractor extends ValidatorExtractor {

  override def extract(params: ValidatorExtractorParameters): Option[ParameterValidator] = {
    params.determinedEditors.toList match {
      case FixedValuesParameterEditor(possibleValues) :: Nil => Some(FixedValuesExpressionValidator(possibleValues))
      case MultiSelectEditor(possibleValues) :: _ => Some(MultiSelectFixedValuesExpressionValidator(possibleValues))
      case _                                      => None
    }
  }

}
