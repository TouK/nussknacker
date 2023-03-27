package pl.touk.nussknacker.engine.definition.parameter.defaults

import pl.touk.nussknacker.engine.api.definition.SpelTemplateParameterEditor
import pl.touk.nussknacker.engine.graph.expression.Expression

protected object OptionalityBasedDefaultValueDeterminer extends ParameterDefaultValueDeterminer {

  override def determineParameterDefaultValue(parameters: DefaultValueDeterminerParameters): Option[Expression] = {
    Option(parameters).filter(_.isOptional).map { _ =>
      val lang = if (parameters.determinedEditor.contains(SpelTemplateParameterEditor)) {
        Expression.Language.SpelTemplate
      } else {
        Expression.Language.Spel
      }
      Expression(lang, "")
    }
  }

}
