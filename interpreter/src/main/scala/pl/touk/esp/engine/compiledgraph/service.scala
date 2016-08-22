package pl.touk.esp.engine.compiledgraph

import pl.touk.esp.engine.compiledgraph.expression.Expression
import pl.touk.esp.engine.definition.ServiceInvoker

object service {

  case class Parameter(name: String, expression: Expression)

  case class ServiceRef(id: String, invoker: ServiceInvoker, parameters: List[Parameter])

}
