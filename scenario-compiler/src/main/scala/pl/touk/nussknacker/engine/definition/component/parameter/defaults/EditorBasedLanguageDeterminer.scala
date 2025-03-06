package pl.touk.nussknacker.engine.definition.component.parameter.defaults

import pl.touk.nussknacker.engine.api.definition._
import pl.touk.nussknacker.engine.graph.expression.Expression

object EditorBasedLanguageDeterminer {

  def determineLanguageOf(editor: Option[ParameterEditor]): Expression.Language = editor match {
    case Some(editor) => determineLanguageOf(editor)
    case None         => Expression.Language.Spel
  }

  private def determineLanguageOf(editor: ParameterEditor): Expression.Language =
    editor match {
      case SpelParameterEditor => Expression.Language.Spel
      case BoolParameterEditor | DateParameterEditor | TimeParameterEditor | DateTimeParameterEditor |
          TextareaParameterEditor | JsonParameterEditor | DurationParameterEditor(_) | PeriodParameterEditor(_) |
          CronParameterEditor | FixedValuesParameterEditor(_) | FixedValuesWithIconParameterEditor(_) |
          FixedValuesWithRadioParameterEditor(_) =>
        Expression.Language.Spel
      case SqlParameterEditor | SpelTemplateParameterEditor =>
        Expression.Language.SpelTemplate
      case DictParameterEditor(_) =>
        Expression.Language.DictKeyWithLabel
      case TabularTypedDataEditor =>
        Expression.Language.TabularDataDefinition
    }

}
