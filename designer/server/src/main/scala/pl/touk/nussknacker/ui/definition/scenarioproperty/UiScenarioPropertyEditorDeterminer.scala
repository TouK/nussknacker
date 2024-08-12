package pl.touk.nussknacker.ui.definition.scenarioproperty

import pl.touk.nussknacker.engine.api.definition.{SimpleParameterEditor, StringParameterEditor}
import pl.touk.nussknacker.engine.api.component.ScenarioPropertiesParameterConfig

object UiScenarioPropertyEditorDeterminer {

  def determine(config: ScenarioPropertiesParameterConfig): SimpleParameterEditor = {
    config.editor match {
      case Some(editor: SimpleParameterEditor) => editor
      case None                                => StringParameterEditor
    }
  }

}
