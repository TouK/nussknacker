package pl.touk.nussknacker.engine.definition.component.parameter.defaults

import pl.touk.nussknacker.engine.api.definition._
import pl.touk.nussknacker.engine.graph.expression.Expression

object EditorBasedLanguageDeterminer {

  def determineLanguageOf(editor: Option[ParameterEditor]): Expression.Language = editor match {
    case Some(RawParameterEditor)                   => Expression.Language.Spel
    case Some(simpleEditor: SimpleParameterEditor)  => determineLanguageOf(simpleEditor)
    case Some(DualParameterEditor(simpleEditor, _)) => determineLanguageOf(simpleEditor)
    case None                                       => Expression.Language.Spel
  }

  private def determineLanguageOf(editor: SimpleParameterEditor): Expression.Language =
    editor match {
      case BoolParameterEditor | StringParameterEditor | DateParameterEditor | TimeParameterEditor |
          DateTimeParameterEditor | TextareaParameterEditor | JsonParameterEditor | DurationParameterEditor(_) |
          PeriodParameterEditor(_) | CronParameterEditor | FixedValuesParameterEditor(_) |
          FixedValuesWithIconParameterEditor(_) =>
        Expression.Language.Spel
      case SqlParameterEditor | SpelTemplateParameterEditor =>
        Expression.Language.SpelTemplate
      case DictParameterEditor(_) =>
        Expression.Language.DictKeyWithLabel
      case TabularTypedDataEditor =>
        Expression.Language.TabularDataDefinition
    }

}
