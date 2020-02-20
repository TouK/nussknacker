package pl.touk.nussknacker.ui.definition.defaults

import pl.touk.nussknacker.engine.api.definition.FixedValuesParameterEditor
import pl.touk.nussknacker.ui.definition.UIParameter

protected object EditorPossibleValuesBasedDefaultValueDeterminer extends ParameterDefaultValueDeterminer {

  override def determineParameterDefaultValue(nodeDefinition: UINodeDefinition, parameter: UIParameter): Option[String] = {
    parameter.editor match {
      case FixedValuesParameterEditor(firstValue :: _) => Some(firstValue.expression)
      case _ => None
    }
  }
}
