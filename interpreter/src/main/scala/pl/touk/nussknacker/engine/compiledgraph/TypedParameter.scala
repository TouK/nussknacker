package pl.touk.nussknacker.engine.compiledgraph

import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.expression.parse.TypedValue

case class TypedParameter(name: ParameterName, typedValue: TypedValue)
