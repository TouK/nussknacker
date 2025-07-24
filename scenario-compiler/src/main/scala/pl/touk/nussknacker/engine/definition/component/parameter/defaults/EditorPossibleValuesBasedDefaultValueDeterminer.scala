package pl.touk.nussknacker.engine.definition.component.parameter.defaults

import pl.touk.nussknacker.engine.api.definition.{
  BoolParameterEditor,
  CronParameterEditor,
  DateParameterEditor,
  DateTimeParameterEditor,
  DictParameterEditor,
  DurationParameterEditor,
  FixedValuesParameterEditor,
  FixedValuesWithIconParameterEditor,
  FixedValuesWithRadioParameterEditor,
  JsonParameterEditor,
  JsonTemplateParameterEditor,
  ParameterEditor,
  PeriodParameterEditor,
  SpelParameterEditor,
  SpelTemplateParameterEditor,
  SqlParameterEditor,
  TabularTypedDataEditor,
  TextareaParameterEditor,
  TimeParameterEditor
}
import pl.touk.nussknacker.engine.graph.expression.{Expression, TabularTypedData}
import pl.touk.nussknacker.engine.graph.expression.Expression.Language

protected object EditorPossibleValuesBasedDefaultValueDeterminer extends ParameterDefaultValueDeterminer {

  override def determineParameterDefaultValue(parameters: DefaultValueDeterminerParameters): Option[Expression] =
    defaultFor(parameters.determinedEditors)

  private def defaultFor(editors: List[ParameterEditor]) = {
    editors
      .collectFirst[Expression](defaultForFixedParameterEditor)
      .orElse(
        editors.headOption.flatMap(defaultForEditor)
      )
  }

  private def defaultForFixedParameterEditor: PartialFunction[ParameterEditor, Expression] = {
    case FixedValuesParameterEditor(firstValue :: _) => Expression.spel(firstValue.expression)
    // it is better to see error that field is not filled instead of strange default value like '' for String
    case FixedValuesParameterEditor(Nil)                      => Expression.spel("")
    case FixedValuesWithIconParameterEditor(firstValue :: _)  => Expression.spel(firstValue.expression)
    case FixedValuesWithIconParameterEditor(Nil)              => Expression.spel("")
    case FixedValuesWithRadioParameterEditor(firstValue :: _) => Expression.spel(firstValue.expression)
    case FixedValuesWithRadioParameterEditor(Nil)             => Expression.spel("")
  }

  private def defaultForEditor(editor: ParameterEditor): Option[Expression] = editor match {
    case JsonParameterEditor                              => Some(Expression.json("{}"))
    case JsonTemplateParameterEditor                      => Some(Expression.jsonTemplate("{}"))
    case SpelTemplateParameterEditor | SqlParameterEditor => Some(Expression.spelTemplate(""))
    case TabularTypedDataEditor     => Some(Expression.tabularDataDefinition(TabularTypedData.empty.stringify))
    case _: DictParameterEditor     => Some(Expression(Language.DictKeyWithLabel, ""))
    case _: DurationParameterEditor => None
    case _: PeriodParameterEditor   => None
    case CronParameterEditor        => None
    case DateParameterEditor        => None
    case TimeParameterEditor        => None
    case DateTimeParameterEditor    => None
    case SpelParameterEditor        => None
    case BoolParameterEditor        => None
    case TextareaParameterEditor    => None
    case _: FixedValuesParameterEditor | _: FixedValuesWithIconParameterEditor |
        _: FixedValuesWithRadioParameterEditor =>
      None
  }

}
