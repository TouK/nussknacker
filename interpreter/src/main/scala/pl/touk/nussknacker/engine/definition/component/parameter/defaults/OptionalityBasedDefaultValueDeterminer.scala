package pl.touk.nussknacker.engine.definition.component.parameter.defaults

import pl.touk.nussknacker.engine.api.definition._
import pl.touk.nussknacker.engine.graph.expression.Expression

protected object OptionalityBasedDefaultValueDeterminer extends ParameterDefaultValueDeterminer {

  override def determineParameterDefaultValue(parameters: DefaultValueDeterminerParameters): Option[Expression] = {
    Option(parameters).filter(_.isOptional).map { _ =>
      val lang = parameters.determinedEditor
        .map {
          case RawParameterEditor => Expression.Language.Spel
          case DictParameterEditor(_) => Expression.Language.DictKeyWithLabel
          case simpleEditor: SimpleParameterEditor => determineLanguage(of = simpleEditor)
          case DualParameterEditor(simpleEditor, _) => determineLanguage(of = simpleEditor)
        }
        .getOrElse(Expression.Language.Spel)

      Expression(lang, "")
    }
  }

  private def determineLanguage(of: SimpleParameterEditor) = {
    of match {
      case BoolParameterEditor | StringParameterEditor | DateParameterEditor | TimeParameterEditor |
           DateTimeParameterEditor | TextareaParameterEditor | JsonParameterEditor | DurationParameterEditor(_) |
           PeriodParameterEditor(_) | CronParameterEditor | FixedValuesParameterEditor(_) =>
        Expression.Language.Spel
      case SqlParameterEditor | SpelTemplateParameterEditor =>
        Expression.Language.SpelTemplate
      case TabularTypedDataEditor =>
        Expression.Language.TabularDataDefinition
    }
  }

}
