package pl.touk.nussknacker.engine.graph

import io.circe.generic.JsonCodec
import pl.touk.nussknacker.engine.graph.evaluatedparam.{Parameter => NodeParameter}
import sttp.tapir.Schema

object service {

  @JsonCodec case class ServiceRef(id: String, parameters: List[NodeParameter])

  object ServiceRef {
    implicit val schema: Schema[ServiceRef] = Schema.derived[ServiceRef]
  }

}
