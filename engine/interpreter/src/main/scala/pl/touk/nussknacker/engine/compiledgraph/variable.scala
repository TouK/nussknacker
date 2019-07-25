package pl.touk.nussknacker.engine.compiledgraph

import pl.touk.nussknacker.engine.api.expression.Expression

object variable {

  case class Field(name: String, expression: Expression)

}
