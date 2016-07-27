package pl.touk.esp.engine.graph

import pl.touk.esp.engine.graph.expression.Expression

object service {

  case class Parameter(name: String, expression: Expression)

  case class ServiceRef(id: String, parameters: List[Parameter])


}
