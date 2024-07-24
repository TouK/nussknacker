package pl.touk.nussknacker.engine.definition.component.parameter.defaults

import pl.touk.nussknacker.engine.api.definition._
import pl.touk.nussknacker.engine.graph.expression.Expression

protected object OptionalityBasedDefaultValueDeterminer extends ParameterDefaultValueDeterminer {

  override def determineParameterDefaultValue(parameters: DefaultValueDeterminerParameters): Option[Expression] = {
    Option(parameters).filter(_.isOptional).map { _ =>
      val lang = parameters.determinedEditor
        .map {
          case RawParameterEditor                   => Expression.Language.Spel
          case simpleEditor: SimpleParameterEditor  => determineLanguageOf(simpleEditor)
          case DualParameterEditor(simpleEditor, _) => determineLanguageOf(simpleEditor)
        }
        .getOrElse {
          Expression.Language.Spel
        }

      Expression(lang, "")
    }
  }

  private def determineLanguageOf(editor: SimpleParameterEditor) = {
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

}
