package pl.touk.esp.engine.graph

import pl.touk.esp.engine.graph.expression.Expression

object variable {

  case class Field(name: String, expression: Expression)

}
