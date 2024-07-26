package pl.touk.nussknacker.engine.definition.component.parameter.defaults

import pl.touk.nussknacker.engine.definition.component.parameter.EditorBasedLanguageDeterminer
import pl.touk.nussknacker.engine.graph.expression.Expression

object ConfigParameterDefaultValueDeterminer extends ParameterDefaultValueDeterminer {

  override def determineParameterDefaultValue(parameters: DefaultValueDeterminerParameters): Option[Expression] = {
    val defaultValueOpt = parameters.parameterConfig.defaultValue

    // TODO: make language configurable as well
    val language = EditorBasedLanguageDeterminer.determineLanguageOf(parameters.determinedEditor)

    defaultValueOpt.map(Expression(language, _))
  }

}
