package pl.touk.nussknacker.engine.definition.validator

import java.lang.reflect.Parameter

import pl.touk.nussknacker.engine.api.definition.{DualParameterEditor, FixedValuesParameterEditor, FixedValuesValidator, ParameterEditor, ParameterValidator}

case class FixedValueValidatorExtractor(possibleEditor: Option[ParameterEditor]) extends ValidatorExtractor {

  override def extract(p: Parameter): Option[ParameterValidator] = {
    possibleEditor match {
      case Some(DualParameterEditor(FixedValuesParameterEditor(possibleValues), _)) => Some(FixedValuesValidator(possibleValues))
      case Some(FixedValuesParameterEditor(possibleValues)) => Some(FixedValuesValidator(possibleValues))
      case _ => None
    }
  }
}
