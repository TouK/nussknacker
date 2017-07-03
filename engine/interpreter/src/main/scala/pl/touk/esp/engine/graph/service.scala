package pl.touk.esp.engine.graph

import pl.touk.esp.engine.graph.evaluatedparam.Parameter

object service {

  case class ServiceRef(id: String, parameters: List[Parameter])


}
