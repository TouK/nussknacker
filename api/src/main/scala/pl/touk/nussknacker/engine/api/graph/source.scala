package pl.touk.nussknacker.engine.api.graph

import io.circe.generic.JsonCodec
import pl.touk.nussknacker.engine.api.graph.evaluatedparam.Parameter

object source {

  @JsonCodec case class SourceRef(typ: String, parameters: List[Parameter])

}
