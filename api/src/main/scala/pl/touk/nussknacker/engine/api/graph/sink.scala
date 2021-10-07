package pl.touk.nussknacker.engine.api.graph

import io.circe.generic.JsonCodec
import pl.touk.nussknacker.engine.api.graph.evaluatedparam.Parameter

object sink {

  @JsonCodec case class SinkRef(typ: String, parameters: List[Parameter])

}
