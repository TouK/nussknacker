package pl.touk.esp.engine.graph

import pl.touk.esp.engine.graph.expression.Expression

object evaluatedparam {

  case class Parameter(name: String, expression: Expression)

}
