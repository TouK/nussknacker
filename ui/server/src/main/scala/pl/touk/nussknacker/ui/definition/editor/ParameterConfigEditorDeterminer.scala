package pl.touk.nussknacker.ui.definition.editor

import pl.touk.nussknacker.engine.api.definition.{Parameter, ParameterEditor}
import pl.touk.nussknacker.engine.api.process.ParameterConfig

protected class ParameterConfigEditorDeterminer(paramConfig: ParameterConfig) extends ParameterEditorDeterminer {

  override def determineParameterEditor(param: Parameter): Option[ParameterEditor] = paramConfig.editor

}
