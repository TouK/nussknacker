package pl.touk.nussknacker.engine.graph

import pl.touk.nussknacker.engine.graph.evaluatedparam.Parameter

object source {

  case class SourceRef(typ: String, parameters: List[Parameter])

}
