package pl.touk.nussknacker.engine.graph

import io.circe.generic.JsonCodec
import pl.touk.nussknacker.engine.graph.evaluatedparam.{Parameter => NodeParameter}

object sink {

  // TODO: rename typ to componentId
  @JsonCodec case class SinkRef(typ: String, parameters: List[NodeParameter])

}
