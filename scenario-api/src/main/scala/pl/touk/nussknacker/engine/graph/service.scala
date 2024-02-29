package pl.touk.nussknacker.engine.graph

import io.circe.generic.JsonCodec
import pl.touk.nussknacker.engine.graph.evaluatedparam.{Parameter => NodeParameter}

object service {

  @JsonCodec case class ServiceRef(id: String, parameters: List[NodeParameter])

}
