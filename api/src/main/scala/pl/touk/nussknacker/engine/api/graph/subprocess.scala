package pl.touk.nussknacker.engine.api.graph

import io.circe.generic.JsonCodec
import pl.touk.nussknacker.engine.api.graph.evaluatedparam.Parameter

object subprocess {

  @JsonCodec case class SubprocessRef(id: String, parameters: List[Parameter])

}
