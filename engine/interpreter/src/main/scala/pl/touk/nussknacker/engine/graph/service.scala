package pl.touk.nussknacker.engine.graph

import io.circe.derivation.annotations.JsonCodec
import pl.touk.nussknacker.engine.graph.evaluatedparam.Parameter

object service {

  @JsonCodec case class ServiceRef(id: String, parameters: List[Parameter])


}
