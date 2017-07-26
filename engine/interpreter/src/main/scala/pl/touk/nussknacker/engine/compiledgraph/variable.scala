package pl.touk.nussknacker.engine.compiledgraph

import pl.touk.nussknacker.engine.compiledgraph.expression.Expression

object variable {

  case class Field(name: String, expression: Expression)

}
