package pl.touk.nussknacker.engine.graph

import pl.touk.nussknacker.engine.graph.evaluatedparam.Parameter

object sink {

  case class SinkRef(typ: String, parameters: List[Parameter])

}