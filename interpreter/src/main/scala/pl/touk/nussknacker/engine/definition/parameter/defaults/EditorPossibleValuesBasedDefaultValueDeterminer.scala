package pl.touk.nussknacker.engine.definition.parameter.defaults

import pl.touk.nussknacker.engine.api.definition.{DualParameterEditor, FixedValuesParameterEditor}
import pl.touk.nussknacker.engine.graph.expression.Expression

protected object EditorPossibleValuesBasedDefaultValueDeterminer extends ParameterDefaultValueDeterminer {

  override def determineParameterDefaultValue(parameters: DefaultValueDeterminerParameters): Option[Expression] = {
    parameters.determinedEditor.flatMap {
      case FixedValuesParameterEditor(firstValue :: _) => Some(firstValue.expression)
      // it is better to see error that field is not filled instead of strange default value like '' for String
      case FixedValuesParameterEditor(Nil) => Some("")
      case DualParameterEditor(FixedValuesParameterEditor(firstValue :: _), _) => Some(firstValue.expression)
      case _ => None
    }.map(Expression.spel)
  }
}
