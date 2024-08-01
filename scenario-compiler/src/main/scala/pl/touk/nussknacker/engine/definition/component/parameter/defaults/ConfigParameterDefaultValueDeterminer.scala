package pl.touk.nussknacker.engine.definition.component.parameter.defaults

import pl.touk.nussknacker.engine.graph.expression.Expression

object ConfigParameterDefaultValueDeterminer extends ParameterDefaultValueDeterminer {

  override def determineParameterDefaultValue(parameters: DefaultValueDeterminerParameters): Option[Expression] = {
    val language = EditorBasedLanguageDeterminer.determineLanguageOf(parameters.determinedEditor)
    parameters.parameterConfig.defaultValue.map(Expression(language, _))
  }

}
