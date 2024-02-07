package pl.touk.nussknacker.engine.definition.component.parameter.defaults

import pl.touk.nussknacker.engine.api.definition.{DictParameterEditor, SpelTemplateParameterEditor, SqlParameterEditor}
import pl.touk.nussknacker.engine.graph.expression.Expression

protected object OptionalityBasedDefaultValueDeterminer extends ParameterDefaultValueDeterminer {

  override def determineParameterDefaultValue(parameters: DefaultValueDeterminerParameters): Option[Expression] = {
    Option(parameters).filter(_.isOptional).map { _ =>
      val lang = parameters.determinedEditor
        .collect {
          case SpelTemplateParameterEditor => Expression.Language.SpelTemplate
          case SqlParameterEditor          => Expression.Language.SpelTemplate
          case DictParameterEditor(_)      => Expression.Language.Literal
        }
        .getOrElse(Expression.Language.Spel)

      Expression(lang, "")
    }
  }

}
