package pl.touk.nussknacker.engine.definition.component.parameter.defaults

import pl.touk.nussknacker.engine.api.DefaultValue
import pl.touk.nussknacker.engine.graph.expression.Expression

object AnnotationDefaultValueDeterminer extends ParameterDefaultValueDeterminer {

  override def determineParameterDefaultValue(parameters: DefaultValueDeterminerParameters): Option[Expression] = {
    parameters.parameterData.getAnnotation[DefaultValue].map(_.value()).map(Expression.spel)
  }

}
