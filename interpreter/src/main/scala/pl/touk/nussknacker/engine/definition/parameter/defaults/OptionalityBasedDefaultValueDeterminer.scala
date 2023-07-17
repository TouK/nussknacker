package pl.touk.nussknacker.engine.definition.parameter.defaults

import pl.touk.nussknacker.engine.api.definition.{SpelTemplateParameterEditor, SqlParameterEditor}
import pl.touk.nussknacker.engine.graph.expression.Expression

protected object OptionalityBasedDefaultValueDeterminer extends ParameterDefaultValueDeterminer {

  override def determineParameterDefaultValue(parameters: DefaultValueDeterminerParameters): Option[Expression] = {
    Option(parameters).filter(_.isOptional).map { _ =>
      val lang = parameters.determinedEditor.collect {
        case SpelTemplateParameterEditor => Expression.Language.SpelTemplate
        case SqlParameterEditor => Expression.Language.SqlSpelTemplate
      }.getOrElse(Expression.Language.Spel)

      Expression(lang, "")
    }
  }

}
