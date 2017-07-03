package pl.touk.esp.engine.graph

import pl.touk.esp.engine.graph.param.Parameter

object sink {

  case class SinkRef(typ: String, parameters: List[Parameter])

}