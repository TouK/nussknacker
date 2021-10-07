package pl.touk.nussknacker.engine.api.graph

import io.circe.generic.JsonCodec
import pl.touk.nussknacker.engine.api.graph.evaluatedparam.Parameter

object service {

  @JsonCodec case class ServiceRef(id: String, parameters: List[Parameter])


}
