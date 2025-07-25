package pl.touk.nussknacker.engine.definition.component.parameter.defaults

import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.graph.expression.Expression

object ConfigParameterDefaultValueDeterminer extends ParameterDefaultValueDeterminer with LazyLogging {

  override def determineParameterDefaultValue(parameters: DefaultValueDeterminerParameters): Option[Expression] = {
    parameters.parameterConfig.defaultValue
  }

}
