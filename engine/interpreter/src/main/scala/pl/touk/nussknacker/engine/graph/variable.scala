package pl.touk.nussknacker.engine.graph

import pl.touk.nussknacker.engine.graph.expression.Expression

object variable {

  case class Field(name: String, expression: Expression)

}
