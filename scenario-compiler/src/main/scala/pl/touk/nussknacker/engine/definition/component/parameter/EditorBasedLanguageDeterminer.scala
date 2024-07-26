package pl.touk.nussknacker.engine.definition.component.parameter

import pl.touk.nussknacker.engine.api.definition.{
  BoolParameterEditor,
  CronParameterEditor,
  DateParameterEditor,
  DateTimeParameterEditor,
  DictParameterEditor,
  DualParameterEditor,
  DurationParameterEditor,
  FixedValuesParameterEditor,
  JsonParameterEditor,
  ParameterEditor,
  PeriodParameterEditor,
  RawParameterEditor,
  SimpleParameterEditor,
  SpelTemplateParameterEditor,
  SqlParameterEditor,
  StringParameterEditor,
  TabularTypedDataEditor,
  TextareaParameterEditor,
  TimeParameterEditor
}
import pl.touk.nussknacker.engine.graph.expression.Expression

// TODO: maybe some better way to specify language like Parameter.language
object EditorBasedLanguageDeterminer {

  def determineLanguageOf(editor: Option[ParameterEditor]): Expression.Language = editor match {
    case Some(RawParameterEditor)                   => Expression.Language.Spel
    case Some(simpleEditor: SimpleParameterEditor)  => determineLanguageOf(simpleEditor)
    case Some(DualParameterEditor(simpleEditor, _)) => determineLanguageOf(simpleEditor)
    case None                                       => Expression.Language.Spel
  }

  private def determineLanguageOf(editor: SimpleParameterEditor) = {
    editor match {
      case BoolParameterEditor | StringParameterEditor | DateParameterEditor | TimeParameterEditor |
          DateTimeParameterEditor | TextareaParameterEditor | JsonParameterEditor | DurationParameterEditor(_) |
          PeriodParameterEditor(_) | CronParameterEditor | FixedValuesParameterEditor(_) =>
        Expression.Language.Spel
      case SqlParameterEditor | SpelTemplateParameterEditor =>
        Expression.Language.SpelTemplate
      case DictParameterEditor(_) =>
        Expression.Language.DictKeyWithLabel
      case TabularTypedDataEditor =>
        Expression.Language.TabularDataDefinition
    }
  }

}
