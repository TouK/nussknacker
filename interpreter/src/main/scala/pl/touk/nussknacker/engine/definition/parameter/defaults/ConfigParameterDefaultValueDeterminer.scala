package pl.touk.nussknacker.engine.definition.parameter.defaults

import pl.touk.nussknacker.engine.graph.expression.Expression

object ConfigParameterDefaultValueDeterminer extends ParameterDefaultValueDeterminer {

  override def determineParameterDefaultValue(parameters: DefaultValueDeterminerParameters): Option[Expression] = {
    // TODO: make language configurable as well
    parameters.parameterConfig.defaultValue.map(Expression.spel)
  }

}