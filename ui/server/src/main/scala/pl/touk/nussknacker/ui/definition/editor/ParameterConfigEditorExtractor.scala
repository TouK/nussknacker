package pl.touk.nussknacker.ui.definition.editor

import pl.touk.nussknacker.engine.api.definition.{Parameter, ParameterEditor}
import pl.touk.nussknacker.engine.api.process.ParameterConfig

protected class ParameterConfigEditorExtractor(paramConfig: ParameterConfig) extends ParameterEditorExtractorStrategy {

  override def evaluateParameterEditor(param: Parameter): Option[ParameterEditor] = paramConfig.editor

}
