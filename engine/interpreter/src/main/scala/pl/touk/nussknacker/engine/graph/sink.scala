package pl.touk.nussknacker.engine.graph

import io.circe.derivation.annotations.JsonCodec
import pl.touk.nussknacker.engine.graph.evaluatedparam.Parameter

object sink {

  @JsonCodec case class SinkRef(typ: String, parameters: List[Parameter])

}