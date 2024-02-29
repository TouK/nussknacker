package pl.touk.nussknacker.engine.graph

import io.circe.generic.JsonCodec
import pl.touk.nussknacker.engine.graph.evaluatedparam.{Parameter => NodeParameter}
import sttp.tapir.Schema

object sink {

  // TODO: rename typ to componentId
  @JsonCodec case class SinkRef(typ: String, parameters: List[NodeParameter])

  object SinkRef {
    implicit val schema: Schema[SinkRef] = Schema.derived[SinkRef]
  }

}
