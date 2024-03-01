package pl.touk.nussknacker.engine.graph

import io.circe.generic.JsonCodec
import pl.touk.nussknacker.engine.graph.expression.Expression
import sttp.tapir.Schema

object variable {

  @JsonCodec case class Field(name: String, expression: Expression)

  object Field {
    implicit val schema: Schema[Field] = Schema.derived
  }

}
