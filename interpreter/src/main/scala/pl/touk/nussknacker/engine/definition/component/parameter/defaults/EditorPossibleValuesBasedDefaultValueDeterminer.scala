package pl.touk.nussknacker.engine.definition.component.parameter.defaults

import pl.touk.nussknacker.engine.api.definition.{
  DualParameterEditor,
  FixedValuesParameterEditor,
  TypedTabularDataEditor
}
import pl.touk.nussknacker.engine.graph.expression.Expression

protected object EditorPossibleValuesBasedDefaultValueDeterminer extends ParameterDefaultValueDeterminer {

  override def determineParameterDefaultValue(parameters: DefaultValueDeterminerParameters): Option[Expression] = {
    parameters.determinedEditor
      .flatMap {
        case FixedValuesParameterEditor(firstValue :: _) => Some(Expression.spel(firstValue.expression))
        // it is better to see error that field is not filled instead of strange default value like '' for String
        case FixedValuesParameterEditor(Nil) => Some(Expression.spel(""))
        case DualParameterEditor(FixedValuesParameterEditor(firstValue :: _), _) =>
          Some(Expression.spel(firstValue.expression))
        case TypedTabularDataEditor => Some(Expression.tabularDataDefinition(""))
        case _                      => None
      }
  }

}
