package pl.touk.nussknacker.ui.definition.defaults

import pl.touk.nussknacker.engine.api.definition.{DualParameterEditor, FixedValuesParameterEditor}
import pl.touk.nussknacker.restmodel.definition.UIParameter

protected object EditorPossibleValuesBasedDefaultValueDeterminer extends ParameterDefaultValueDeterminer {

  override def determineParameterDefaultValue(nodeDefinition: UINodeDefinition, parameter: UIParameter): Option[String] = {
    parameter.editor match {
      case FixedValuesParameterEditor(firstValue :: _) => Some(firstValue.expression)
      // it is better to see error that field is not filled instead of strange default value like '' for String
      case FixedValuesParameterEditor(Nil) => Some("")
      case DualParameterEditor(FixedValuesParameterEditor(firstValue :: _), _) => Some(firstValue.expression)
      case _ => None
    }
  }
}
