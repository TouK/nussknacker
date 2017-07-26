package pl.touk.nussknacker.engine.graph

import pl.touk.nussknacker.engine.graph.param.Parameter

object sink {

  case class SinkRef(typ: String, parameters: List[Parameter])

}