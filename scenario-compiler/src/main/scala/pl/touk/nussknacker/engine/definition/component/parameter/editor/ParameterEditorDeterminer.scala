package pl.touk.nussknacker.engine.definition.component.parameter.editor

import pl.touk.nussknacker.engine.api.definition.ParameterEditors

trait ParameterEditorDeterminer {

  def determine(): Option[ParameterEditors]

}
