package pl.touk.nussknacker.ui.definition.scenarioproperty

import pl.touk.nussknacker.engine.api.definition.{
  FixedValuesParameterEditor,
  FixedValuesPresetParameterEditor,
  FixedValuesValidator,
  JsonParameterEditor,
  JsonValidator,
  ParameterValidator,
  SimpleParameterEditor
}

protected class ScenarioPropertyEditorValidatorDeterminer(editor: Option[SimpleParameterEditor])
    extends ScenarioPropertyValidatorDeterminer {

  override def determine(): Option[List[ParameterValidator]] = {
    editor match {
      case Some(editor: FixedValuesParameterEditor) => Some(List(FixedValuesValidator(editor.possibleValues)))
      case Some(JsonParameterEditor)                => Some(List(JsonValidator))
      case _                                        => None
    }
  }

}
