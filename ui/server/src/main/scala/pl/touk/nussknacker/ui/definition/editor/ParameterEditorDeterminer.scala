package pl.touk.nussknacker.ui.definition.editor

import pl.touk.nussknacker.engine.api.definition.{Parameter, ParameterEditor}

trait ParameterEditorDeterminer {

  def determineParameterEditor(param: Parameter): Option[ParameterEditor]

}
