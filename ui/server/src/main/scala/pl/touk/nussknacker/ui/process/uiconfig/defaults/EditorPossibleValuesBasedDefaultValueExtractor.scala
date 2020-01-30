package pl.touk.nussknacker.ui.process.uiconfig.defaults

import pl.touk.nussknacker.engine.api.definition
import pl.touk.nussknacker.engine.api.definition.FixedValuesParameterEditor
import pl.touk.nussknacker.engine.definition.defaults.{NodeDefinition, ParameterDefaultValueExtractorStrategy}

object EditorPossibleValuesBasedDefaultValueExtractor extends ParameterDefaultValueExtractorStrategy {

  override def evaluateParameterDefaultValue(nodeDefinition: NodeDefinition, parameter: definition.Parameter): Option[String] = {
    parameter.editor.collect { case FixedValuesParameterEditor(firstValue :: _) =>
      firstValue.expression
    }
  }
}

