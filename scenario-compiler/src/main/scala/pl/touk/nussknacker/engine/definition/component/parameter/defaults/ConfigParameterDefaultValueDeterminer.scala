package pl.touk.nussknacker.engine.definition.component.parameter.defaults

import pl.touk.nussknacker.engine.graph.expression.Expression

object ConfigParameterDefaultValueDeterminer extends ParameterDefaultValueDeterminer {

  override def determineParameterDefaultValue(parameters: DefaultValueDeterminerParameters): Option[Expression] = {
//    val language = EditorBasedLanguageDeterminer.determineLanguageOf(parameters.determinedEditors.headOption)
    parameters.parameterConfig.defaultValue // todomkp check if editor matches default ??
  }

}
