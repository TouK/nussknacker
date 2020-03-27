package pl.touk.nussknacker.engine.definition

import pl.touk.nussknacker.engine.api.definition.ParameterEditor

trait ParameterEditorDeterminer {

  def determine(): Option[ParameterEditor]

}
