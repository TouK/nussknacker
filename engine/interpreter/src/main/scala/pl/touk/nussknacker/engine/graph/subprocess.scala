package pl.touk.nussknacker.engine.graph

import io.circe.derivation.annotations.JsonCodec
import pl.touk.nussknacker.engine.graph.evaluatedparam.Parameter

object subprocess {

  @JsonCodec case class SubprocessRef(id: String, parameters: List[Parameter])

}
