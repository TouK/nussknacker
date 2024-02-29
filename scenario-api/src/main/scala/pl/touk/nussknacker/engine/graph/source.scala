package pl.touk.nussknacker.engine.graph

import io.circe.generic.JsonCodec
import pl.touk.nussknacker.engine.graph.evaluatedparam.{Parameter => NodeParameter}
import sttp.tapir.Schema

object source {

  // TODO: rename typ to componentId
  @JsonCodec case class SourceRef(typ: String, parameters: List[NodeParameter])

  object SourceRef {
    implicit val schema: Schema[SourceRef] = Schema.derived[SourceRef]
  }

}
