package pl.touk.nussknacker.ui.definition.scenarioproperty

import pl.touk.nussknacker.engine.api.component.ScenarioPropertyConfig
import pl.touk.nussknacker.engine.api.definition.{ConstStringParameterEditor, StaticParameterEditor}

object UiScenarioPropertyEditorDeterminer {

  def determine(config: ScenarioPropertyConfig): StaticParameterEditor = {
    config.editor match {
      case Some(editor: StaticParameterEditor) => editor
      case None                                => ConstStringParameterEditor
    }
  }

}
