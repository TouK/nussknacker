package pl.touk.nussknacker.ui.definition.editor

import pl.touk.nussknacker.engine.api.definition.ParameterEditor
import pl.touk.nussknacker.engine.api.process.ParameterConfig
import pl.touk.nussknacker.engine.definition.ParameterEditorDeterminer

protected class ParameterConfigEditorDeterminer(paramConfig: ParameterConfig) extends ParameterEditorDeterminer {

  override def determine(): Option[ParameterEditor] = paramConfig.editor

}
