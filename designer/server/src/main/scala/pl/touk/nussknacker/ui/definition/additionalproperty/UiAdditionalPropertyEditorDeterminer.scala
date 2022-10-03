package pl.touk.nussknacker.ui.definition.additionalproperty

import pl.touk.nussknacker.engine.api.definition.{SimpleParameterEditor, StringParameterEditor}
import pl.touk.nussknacker.engine.api.component.AdditionalPropertyConfig

object UiAdditionalPropertyEditorDeterminer {

  def determine(config: AdditionalPropertyConfig): SimpleParameterEditor = {
    config.editor match {
      case Some(editor: SimpleParameterEditor) => editor
      case None => StringParameterEditor
    }
  }
}
