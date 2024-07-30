package pl.touk.nussknacker.engine.definition.component.parameter.defaults

import pl.touk.nussknacker.engine.api.definition._
import pl.touk.nussknacker.engine.graph.expression.Expression

trait SimpleParameterEditorHelper {

  def calculateDefaultValue(
      determinedEditor: Option[ParameterEditor],
      defaultValue: Option[String]
  ): Option[Expression] =
    for {
      value <- defaultValue
      language <- determinedEditor
        .collect {
          case RawParameterEditor                   => Expression.Language.Spel
          case simpleEditor: SimpleParameterEditor  => determineLanguageOf(simpleEditor)
          case DualParameterEditor(simpleEditor, _) => determineLanguageOf(simpleEditor)
        } orElse Some(Expression.Language.Spel)
    } yield Expression(language, value)

  private def determineLanguageOf(editor: SimpleParameterEditor): Expression.Language =
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
