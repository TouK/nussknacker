package pl.touk.nussknacker.ui.definition.editor

import pl.touk.nussknacker.engine.api.definition.{Parameter, ParameterEditor}

trait ParameterEditorExtractorStrategy {

  def evaluateParameterEditor(param: Parameter): Option[ParameterEditor]

}
