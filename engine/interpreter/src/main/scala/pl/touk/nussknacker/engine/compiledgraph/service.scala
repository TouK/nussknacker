package pl.touk.nussknacker.engine.compiledgraph

import pl.touk.nussknacker.engine.compiledgraph.evaluatedparam.Parameter
import pl.touk.nussknacker.engine.definition.ServiceInvoker

object service {

  case class ServiceRef(id: String, invoker: ServiceInvoker, parameters: List[Parameter])

}
