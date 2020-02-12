package pl.touk.nussknacker.ui.definition.editor

import pl.touk.nussknacker.engine.api.definition.{Parameter, ParameterEditor}

protected object ParameterBasedEditorDeterminer extends ParameterEditorDeterminer {

  override def determineParameterEditor(param: Parameter): Option[ParameterEditor] = param.editor

}
