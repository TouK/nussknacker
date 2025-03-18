package pl.touk.nussknacker.ui.definition.scenarioproperty

import pl.touk.nussknacker.engine.api.definition.{
  FixedValuesParameterEditor,
  FixedValuesValidator,
  JsonParameterEditor,
  JsonValidator,
  ParameterEditor,
  ParameterValidator
}

protected class ScenarioPropertyEditorValidatorDeterminer(editor: Option[ParameterEditor])
    extends ScenarioPropertyValidatorDeterminer {

  override def determine(): Option[List[ParameterValidator]] = {
    editor match {
      case Some(editor: FixedValuesParameterEditor) => Some(List(FixedValuesValidator(editor.possibleValues)))
      case Some(JsonParameterEditor)                => Some(List(JsonValidator))
      case _                                        => None
    }
  }

}
