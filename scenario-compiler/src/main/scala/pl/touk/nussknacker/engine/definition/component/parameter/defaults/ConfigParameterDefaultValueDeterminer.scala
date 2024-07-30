package pl.touk.nussknacker.engine.definition.component.parameter.defaults

import pl.touk.nussknacker.engine.graph.expression.Expression

object ConfigParameterDefaultValueDeterminer extends ParameterDefaultValueDeterminer with SimpleParameterEditorHelper {

  override def determineParameterDefaultValue(parameters: DefaultValueDeterminerParameters): Option[Expression] =
    calculateDefaultValue(parameters.determinedEditor, parameters.parameterConfig.defaultValue)

}
