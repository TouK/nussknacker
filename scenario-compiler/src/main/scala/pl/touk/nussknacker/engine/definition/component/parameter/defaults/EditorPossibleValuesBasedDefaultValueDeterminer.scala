package pl.touk.nussknacker.engine.definition.component.parameter.defaults

import pl.touk.nussknacker.engine.api.definition.{
  DictParameterEditor,
  DualParameterEditor,
  FixedValuesParameterEditor,
  SpelTemplateParameterEditor,
  TabularTypedDataEditor
}
import pl.touk.nussknacker.engine.graph.expression.{Expression, TabularTypedData}
import pl.touk.nussknacker.engine.graph.expression.Expression.Language

protected object EditorPossibleValuesBasedDefaultValueDeterminer extends ParameterDefaultValueDeterminer {

  override def determineParameterDefaultValue(parameters: DefaultValueDeterminerParameters): Option[Expression] = {
    parameters.determinedEditor
      .flatMap {
        case FixedValuesParameterEditor(firstValue :: _) => Some(Expression.spel(firstValue.expression))
        // it is better to see error that field is not filled instead of strange default value like '' for String
        case FixedValuesParameterEditor(Nil) => Some(Expression.spel(""))
        case DualParameterEditor(FixedValuesParameterEditor(firstValue :: _), _) =>
          Some(Expression.spel(firstValue.expression))
        case TabularTypedDataEditor =>
          Some(Expression.tabularDataDefinition(TabularTypedData.empty.stringify))
        case SpelTemplateParameterEditor =>
          Some(Expression.spelTemplate(""))
        case DictParameterEditor(_) => Some(Expression(Language.DictKeyWithLabel, ""))
        case _                      => None
      }
  }

}
