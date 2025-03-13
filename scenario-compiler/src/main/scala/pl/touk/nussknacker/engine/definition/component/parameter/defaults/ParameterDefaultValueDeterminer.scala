package pl.touk.nussknacker.engine.definition.component.parameter.defaults

import pl.touk.nussknacker.engine.api.component.ParameterConfig
import pl.touk.nussknacker.engine.api.definition.ParameterEditors
import pl.touk.nussknacker.engine.definition.component.parameter.ParameterData
import pl.touk.nussknacker.engine.graph.expression.Expression

trait ParameterDefaultValueDeterminer {
  def determineParameterDefaultValue(parameters: DefaultValueDeterminerParameters): Option[Expression]
}

case class DefaultValueDeterminerParameters(
    parameterData: ParameterData,
    isOptional: Boolean,
    parameterConfig: ParameterConfig,
    determinedEditors: Option[ParameterEditors]
)
