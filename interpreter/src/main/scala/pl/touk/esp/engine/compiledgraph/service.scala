package pl.touk.esp.engine.compiledgraph

import pl.touk.esp.engine.compiledgraph.evaluatedparam.Parameter
import pl.touk.esp.engine.definition.ServiceInvoker

object service {

  case class ServiceRef(id: String, invoker: ServiceInvoker, parameters: List[Parameter])

}
