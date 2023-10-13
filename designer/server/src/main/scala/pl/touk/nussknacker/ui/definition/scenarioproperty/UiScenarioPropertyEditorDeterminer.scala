package pl.touk.nussknacker.ui.definition.scenarioproperty

import pl.touk.nussknacker.engine.api.definition.{SimpleParameterEditor, StringParameterEditor}
import pl.touk.nussknacker.engine.api.component.ScenarioPropertyConfig

object UiScenarioPropertyEditorDeterminer {

  def determine(config: ScenarioPropertyConfig): SimpleParameterEditor = {
    config.editor match {
      case Some(editor: SimpleParameterEditor) => editor
      case None                                => StringParameterEditor
    }
  }

}
