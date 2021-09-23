package pl.touk.nussknacker.engine.graph

import io.circe.derivation.annotations.JsonCodec
import pl.touk.nussknacker.engine.graph.evaluatedparam.Parameter

object source {

  @JsonCodec case class SourceRef(typ: String, parameters: List[Parameter])

}
