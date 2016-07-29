package pl.touk.esp.engine.compiledgraph

import pl.touk.esp.engine.compiledgraph.expression.Expression

object service {

  case class Parameter(name: String, expression: Expression)

  case class ServiceRef(id: String, parameters: List[Parameter])

}
