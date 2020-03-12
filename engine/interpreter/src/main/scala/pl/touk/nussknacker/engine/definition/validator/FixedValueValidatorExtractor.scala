package pl.touk.nussknacker.engine.definition.validator

import java.lang.reflect.Parameter

import pl.touk.nussknacker.engine.api.definition.{DualParameterEditor, FixedValuesParameterEditor, FixedValuesValidator, ParameterEditor, ParameterValidator}

case class FixedValueValidatorExtractor(possibleEditor: Option[ParameterEditor]) extends ValidatorExtractor {

  override def extract(p: Parameter): Option[ParameterValidator] = {
    possibleEditor match {
      case Some(editor) => extractValidator(editor)
      case None => None
    }
  }

  private def extractValidator(editor: ParameterEditor): Option[ParameterValidator] = {
    editor match {
      case DualParameterEditor(simpleEditor: FixedValuesParameterEditor, _) => fixedValuesValidator(simpleEditor)
      case editor: FixedValuesParameterEditor => fixedValuesValidator(editor)
      case _ => None
    }
  }

  private def fixedValuesValidator(editor: FixedValuesParameterEditor) = {
    Some(FixedValuesValidator(editor.possibleValues))
  }
}
