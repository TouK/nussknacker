package pl.touk.esp.engine.compiledgraph

import pl.touk.esp.engine.compiledgraph.expression.Expression

object evaluatedparam {

  case class Parameter(name: String, expression: Expression)

}
