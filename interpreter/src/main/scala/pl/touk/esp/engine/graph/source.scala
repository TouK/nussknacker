package pl.touk.esp.engine.graph

import pl.touk.esp.engine.graph.param.Parameter

object source {

  case class SourceRef(typ: String, parameters: List[Parameter])

}
