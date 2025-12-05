package pl.touk.nussknacker.engine.definition.component.parameter.defaults

import pl.touk.nussknacker.engine.api.definition._
import pl.touk.nussknacker.engine.graph.expression.Expression

object EditorBasedLanguageDeterminer {

  def determineLanguageOf(editor: ParameterEditor): Expression.Language =
    editor match {
      case SpelParameterEditor => Expression.Language.Spel
      case BoolParameterEditor | DateParameterEditor | TimeParameterEditor | DateTimeParameterEditor |
          TextareaParameterEditor | DurationParameterEditor(_) | PeriodParameterEditor(_) | CronParameterEditor |
          FixedValuesParameterEditor(_) | FixedValuesWithIconParameterEditor(_) |
          FixedValuesWithRadioParameterEditor(_) | SpelExpressionsListEditor =>
        Expression.Language.Spel
      case JsonParameterEditor | MultiSelectEditor(_) => Expression.Language.Json
      case JsonTemplateParameterEditor                => Expression.Language.JsonTemplate
      case SqlParameterEditor | SpelTemplateParameterEditor =>
        Expression.Language.SpelTemplate
      case DictParameterEditor(_) =>
        Expression.Language.DictKeyWithLabel
      case TabularTypedDataEditor =>
        Expression.Language.TabularDataDefinition
    }

}
