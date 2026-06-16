package pl.touk.nussknacker.ui.definition.scenarioproperty

import pl.touk.nussknacker.engine.api.definition.{
  FixedValuesExpressionValidator,
  FixedValuesParameterEditor,
  JsonExpressionValidator,
  JsonParameterEditor,
  ParameterValidator,
  StaticParameterEditor
}

protected class ScenarioPropertyEditorValidatorDeterminer(editor: Option[StaticParameterEditor])
    extends ScenarioPropertyValidatorDeterminer {

  override def determine(): Option[List[ParameterValidator]] = {
    editor match {
      case Some(editor: FixedValuesParameterEditor) => Some(List(FixedValuesExpressionValidator(editor.possibleValues)))
      case Some(JsonParameterEditor)                => Some(List(JsonExpressionValidator))
      case _                                        => None
    }
  }

}
