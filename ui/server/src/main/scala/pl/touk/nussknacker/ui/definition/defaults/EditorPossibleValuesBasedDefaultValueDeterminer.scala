package pl.touk.nussknacker.ui.definition.defaults

import pl.touk.nussknacker.engine.api.defaults.{NodeDefinition, ParameterDefaultValueDeterminer}
import pl.touk.nussknacker.engine.api.definition
import pl.touk.nussknacker.engine.api.definition.FixedValuesParameterEditor

object EditorPossibleValuesBasedDefaultValueDeterminer extends ParameterDefaultValueDeterminer {

  override def determineParameterDefaultValue(nodeDefinition: NodeDefinition, parameter: definition.Parameter): Option[String] = {
    parameter.editor.collect { case FixedValuesParameterEditor(firstValue :: _) =>
      firstValue.expression
    }
  }
}
