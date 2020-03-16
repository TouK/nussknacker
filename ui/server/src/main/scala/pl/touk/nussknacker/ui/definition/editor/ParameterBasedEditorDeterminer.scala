package pl.touk.nussknacker.ui.definition.editor

import pl.touk.nussknacker.engine.api.definition.{Parameter, ParameterEditor}
import pl.touk.nussknacker.engine.definition.ParameterEditorDeterminer

protected class ParameterBasedEditorDeterminer(param: Parameter) extends ParameterEditorDeterminer {

  override def determine(): Option[ParameterEditor] = param.editor

}
