package pl.touk.esp.engine.compiledgraph

import pl.touk.esp.engine.compiledgraph.expression.Expression

object variable {

  case class Field(name: String, expression: Expression)

}
