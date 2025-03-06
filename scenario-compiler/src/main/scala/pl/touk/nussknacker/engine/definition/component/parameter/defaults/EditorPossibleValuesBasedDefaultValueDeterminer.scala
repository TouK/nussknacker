package pl.touk.nussknacker.engine.definition.component.parameter.defaults

import pl.touk.nussknacker.engine.api.definition.{
  DictParameterEditor,
  FixedValuesParameterEditor,
  SpelTemplateParameterEditor,
  SqlParameterEditor,
  TabularTypedDataEditor
}
import pl.touk.nussknacker.engine.graph.expression.{Expression, TabularTypedData}
import pl.touk.nussknacker.engine.graph.expression.Expression.Language

protected object EditorPossibleValuesBasedDefaultValueDeterminer extends ParameterDefaultValueDeterminer {

  override def determineParameterDefaultValue(parameters: DefaultValueDeterminerParameters): Option[Expression] = {
    parameters.determinedEditors match {
      case FixedValuesParameterEditor(firstValue :: _) :: Nil => Some(Expression.spel(firstValue.expression))
      // it is better to see error that field is not filled instead of strange default value like '' for String
      case FixedValuesParameterEditor(Nil) :: Nil => Some(Expression.spel(""))
      case TabularTypedDataEditor :: Nil => Some(Expression.tabularDataDefinition(TabularTypedData.empty.stringify))
      case (SpelTemplateParameterEditor | SqlParameterEditor) :: Nil => Some(Expression.spelTemplate(""))
      case DictParameterEditor(_) :: Nil                             => Some(Expression(Language.DictKeyWithLabel, ""))
      case FixedValuesParameterEditor(firstValue :: _) :: _ :: Nil   => Some(Expression.spel(firstValue.expression))
      case _ :: FixedValuesParameterEditor(firstValue :: _) :: Nil   => Some(Expression.spel(firstValue.expression))
      case _                                                         => None
    }
  }

}
