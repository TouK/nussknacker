package pl.touk.nussknacker.ui.definition.additionalproperty

import pl.touk.nussknacker.engine.api.definition.{
  FixedValuesParameterEditor,
  FixedValuesPresetParameterEditor,
  FixedValuesValidator,
  JsonParameterEditor,
  JsonValidator,
  ParameterValidator,
  SimpleParameterEditor
}

protected class AdditionalPropertyEditorValidatorDeterminer(editor: Option[SimpleParameterEditor])
    extends AdditionalPropertyValidatorDeterminer {

  override def determine(): Option[List[ParameterValidator]] = {
    editor match {
      case Some(editor: FixedValuesParameterEditor)       => Some(List(FixedValuesValidator(editor.possibleValues)))
      case Some(editor: FixedValuesPresetParameterEditor) => Some(List(FixedValuesValidator(editor.possibleValues)))
      case Some(JsonParameterEditor)                      => Some(List(JsonValidator))
      case _                                              => None
    }
  }

}
