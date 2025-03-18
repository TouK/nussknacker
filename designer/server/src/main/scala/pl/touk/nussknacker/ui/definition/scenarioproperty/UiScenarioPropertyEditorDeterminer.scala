package pl.touk.nussknacker.ui.definition.scenarioproperty

import pl.touk.nussknacker.engine.api.component.ScenarioPropertyConfig
import pl.touk.nussknacker.engine.api.definition.{ParameterEditor, SpelParameterEditor, SpelTemplateParameterEditor}

object UiScenarioPropertyEditorDeterminer {

  def determine(config: ScenarioPropertyConfig): ParameterEditor = {
    config.editor match {
      case Some(SpelParameterEditor) => SpelTemplateParameterEditor
      case Some(editor)              => editor
      case None                      => SpelTemplateParameterEditor
    }
  }

}
