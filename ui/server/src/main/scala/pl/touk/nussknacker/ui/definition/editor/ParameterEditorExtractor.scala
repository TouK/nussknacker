package pl.touk.nussknacker.ui.definition.editor

import pl.touk.nussknacker.engine.api.definition.{Parameter, ParameterEditor}

protected object ParameterEditorExtractor extends ParameterEditorExtractorStrategy {

  override def evaluateParameterEditor(param: Parameter): Option[ParameterEditor] = param.editor

}
