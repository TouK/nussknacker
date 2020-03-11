package pl.touk.nussknacker.ui.definition.additionalproperty

import pl.touk.nussknacker.engine.api.definition.{FixedValuesParameterEditor, FixedValuesValidator, ParameterValidator, SimpleParameterEditor}

protected class AdditionalPropertyEditorValidatorDeterminer(editor: Option[SimpleParameterEditor]) extends AdditionalPropertyValidatorDeterminer {

  override def determine(): Option[List[ParameterValidator]] = {
    editor match {
      case Some(editor: FixedValuesParameterEditor) => Some(List(FixedValuesValidator(editor.possibleValues)))
      case _ => None
    }
  }
}
