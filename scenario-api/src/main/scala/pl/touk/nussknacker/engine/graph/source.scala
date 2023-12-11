package pl.touk.nussknacker.engine.graph

import io.circe.generic.JsonCodec
import pl.touk.nussknacker.engine.graph.evaluatedparam.Parameter

object source {

  // TODO: rename typ to componentId
  @JsonCodec case class SourceRef(typ: String, parameters: List[Parameter])

}
