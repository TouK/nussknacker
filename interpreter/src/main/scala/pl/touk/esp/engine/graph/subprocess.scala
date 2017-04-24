package pl.touk.esp.engine.graph

import pl.touk.esp.engine.graph.evaluatedparam.Parameter

object subprocess {

  case class SubprocessRef(id: String, parameters: List[Parameter])

}
