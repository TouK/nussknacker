package pl.touk.nussknacker.engine.graph

import pl.touk.nussknacker.engine.graph.evaluatedparam.Parameter

object service {

  case class ServiceRef(id: String, parameters: List[Parameter])


}
